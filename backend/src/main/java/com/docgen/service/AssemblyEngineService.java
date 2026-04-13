package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblyResult;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.SegmentRenderResult;
import com.docgen.entity.ExpressionType;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembly engine that orchestrates the rendering and merging of multiple Segments
 * into a single composite document.
 *
 * <p>Flow: iterate Assembly_Config → evaluate condition expressions → apply DataScope
 * → render each Segment → call /merge-segments to merge.</p>
 *
 * <p>Validates: Requirements 7.5, 7.6, 8.1, 8.2, 8.3, 8.8, 8.9</p>
 */
@Service
public class AssemblyEngineService {

    private static final Logger log = LoggerFactory.getLogger(AssemblyEngineService.class);

    private final SegmentRendererService segmentRendererService;
    private final SegmentDataScopeService segmentDataScopeService;
    private final ExpressionEngine expressionEngine;
    private final RestTemplate restTemplate;
    private final CircuitBreaker docxtemplaterCb;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    public AssemblyEngineService(SegmentRendererService segmentRendererService,
                                 SegmentDataScopeService segmentDataScopeService,
                                 ExpressionEngine expressionEngine,
                                 RestTemplate restTemplate,
                                 CircuitBreaker docxtemplaterCircuitBreaker) {
        this.segmentRendererService = segmentRendererService;
        this.segmentDataScopeService = segmentDataScopeService;
        this.expressionEngine = expressionEngine;
        this.restTemplate = restTemplate;
        this.docxtemplaterCb = docxtemplaterCircuitBreaker;
    }

    /**
     * Assemble a composite document from the given Assembly_Config and global data.
     *
     * <ol>
     *   <li>Iterate segments sorted by position</li>
     *   <li>Skip disabled segments</li>
     *   <li>Evaluate condition expressions — skip if false</li>
     *   <li>Apply DataScope mapping</li>
     *   <li>Render each segment via SegmentRendererService (partial failure mode)</li>
     *   <li>Call /merge-segments to merge all rendered segments</li>
     * </ol>
     *
     * @param config     the assembly configuration
     * @param globalData the global data context
     * @return assembly result with merged document bytes and per-segment stats
     */
    public AssemblyResult assembleDocument(AssemblyConfigDTO config, Map<String, Object> globalData) {
        long totalStart = System.currentTimeMillis();

        List<AssemblySegmentEntry> sortedEntries = config.getSegments().stream()
                .sorted(Comparator.comparingInt(e -> e.getPosition() != null ? e.getPosition() : 0))
                .collect(Collectors.toList());

        List<SegmentRenderResult> allResults = new ArrayList<>();
        List<RenderedSegmentForMerge> toMerge = new ArrayList<>();

        for (AssemblySegmentEntry entry : sortedEntries) {
            // Skip disabled segments
            if (!entry.isEnabled()) {
                log.debug("Segment {} is disabled, skipping", entry.getSegmentId());
                continue;
            }

            // Evaluate condition expression
            if (!evaluateCondition(entry.getConditionExpression(), globalData)) {
                log.debug("Segment {} condition evaluated to false, skipping", entry.getSegmentId());
                SegmentRenderResult skipped = new SegmentRenderResult();
                skipped.setSegmentId(entry.getSegmentId());
                skipped.setSuccess(true);
                skipped.setRenderTimeMs(0);
                allResults.add(skipped);
                continue;
            }

            // Apply DataScope
            Map<String, Object> scopedData = segmentDataScopeService.resolveDataScope(globalData, entry.getDataScope());

            // Render segment (safe mode — partial failure)
            SegmentRenderResult renderResult = segmentRendererService.renderSegmentSafe(
                    entry.getSegmentId(), entry.getLockedVersion(), scopedData);
            allResults.add(renderResult);

            if (renderResult.isSuccess() && renderResult.getRenderedBytes() != null) {
                toMerge.add(new RenderedSegmentForMerge(
                        renderResult.getRenderedBytes(), entry.isPageBreakBefore()));
            }
        }

        // All segments skipped
        if (toMerge.isEmpty()) {
            long totalElapsed = System.currentTimeMillis() - totalStart;
            log.info("All segments were skipped or failed during assembly");
            throw new BusinessException(ErrorCode.GENERATE_ALL_SEGMENTS_SKIPPED,
                    "All segments were skipped by condition expressions or disabled",
                    HttpStatus.OK);
        }

        // Merge segments via /merge-segments
        byte[] mergedBytes = mergeSegments(toMerge);

        long totalElapsed = System.currentTimeMillis() - totalStart;

        AssemblyResult result = new AssemblyResult();
        result.setDocumentBytes(mergedBytes);
        result.setSegmentResults(allResults);
        result.setTotalRenderTimeMs(totalElapsed);

        log.info("Assembly completed: {} segments rendered, {} merged, total time {}ms",
                allResults.size(), toMerge.size(), totalElapsed);

        return result;
    }

    /**
     * Evaluate a condition expression. Returns true if expression is null/blank (default include).
     */
    boolean evaluateCondition(String conditionExpression, Map<String, Object> data) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            return true;
        }

        try {
            Object result = expressionEngine.evaluate(conditionExpression, ExpressionType.JAVASCRIPT, data);
            return Boolean.TRUE.equals(result) || "true".equals(String.valueOf(result));
        } catch (Exception e) {
            log.warn("Condition expression evaluation failed: '{}', treating as false: {}",
                    conditionExpression, e.getMessage());
            return false;
        }
    }

    /**
     * Call the Docxtemplater /merge-segments endpoint to merge rendered segments.
     */
    byte[] mergeSegments(List<RenderedSegmentForMerge> segments) {
        List<Map<String, Object>> segmentPayloads = segments.stream()
                .map(s -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("buffer", Base64.getEncoder().encodeToString(s.bytes));
                    payload.put("pageBreakBefore", s.pageBreakBefore);
                    return payload;
                })
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("segments", segmentPayloads);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            return docxtemplaterCb.executeSupplier(() -> {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        docxtemplaterServiceUrl + "/merge-segments",
                        HttpMethod.POST, entity, byte[].class);

                if (response.getBody() == null || response.getBody().length == 0) {
                    throw new BusinessException(ErrorCode.MERGE_FAILED,
                            "Merge segments returned empty result",
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return response.getBody();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Merge segments call failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.MERGE_FAILED,
                    "Merge segments failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Internal record for holding rendered segment bytes and page break config.
     */
    static class RenderedSegmentForMerge {
        final byte[] bytes;
        final boolean pageBreakBefore;

        RenderedSegmentForMerge(byte[] bytes, boolean pageBreakBefore) {
            this.bytes = bytes;
            this.pageBreakBefore = pageBreakBefore;
        }
    }
}
