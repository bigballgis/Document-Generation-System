package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblyResult;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.SegmentRenderResult;
import com.docgen.entity.ExpressionType;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembly engine that orchestrates the rendering and merging of multiple Segments
 * into a single composite document.
 *
 * <p>Flow: iterate Assembly_Config → evaluate condition expressions → apply DataScope
 * → render each Segment via Docxtemplater /render → call /merge-segments to merge.</p>
 *
 * <p>Validates: Requirements 3.4, 3.5, 3.6, 7.5, 7.6, 8.1, 8.2, 8.3, 8.8, 8.9</p>
 */
/*
 * MinIO Orphan Segment File Cleanup Procedure:
 * After the segment library removal (V36 migration), some MinIO files under the
 * "segments/" prefix may no longer be referenced by any composite template's
 * assembly_config. To identify and clean up these orphan files:
 *
 * 1. List all file paths under "segments/" prefix in the MinIO bucket.
 * 2. Query all templates WHERE template_type='COMPOSITE' and extract filePath
 *    values from assembly_config->'segments' JSONB array.
 * 3. Compute the difference: files in MinIO but not referenced by any template.
 * 4. Review the orphan list manually before deletion.
 * 5. Delete confirmed orphan files from MinIO.
 *
 * Example SQL to get all referenced file paths:
 *   SELECT DISTINCT elem->>'filePath' AS file_path
 *   FROM templates, jsonb_array_elements(assembly_config->'segments') AS elem
 *   WHERE template_type = 'COMPOSITE' AND assembly_config IS NOT NULL;
 */
@Service
public class AssemblyEngineService {

    private static final Logger log = LoggerFactory.getLogger(AssemblyEngineService.class);

    private final ExpressionEngine expressionEngine;
    private final RestTemplate restTemplate;
    private final CircuitBreaker docxtemplaterCb;
    private final MinioClient minioClient;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public AssemblyEngineService(ExpressionEngine expressionEngine,
                                 RestTemplate restTemplate,
                                 CircuitBreaker docxtemplaterCircuitBreaker,
                                 MinioClient minioClient) {
        this.expressionEngine = expressionEngine;
        this.restTemplate = restTemplate;
        this.docxtemplaterCb = docxtemplaterCircuitBreaker;
        this.minioClient = minioClient;
    }

    /**
     * Assemble a composite document from the given Assembly_Config and global data.
     *
     * <ol>
     *   <li>Iterate segments sorted by position</li>
     *   <li>Skip disabled segments</li>
     *   <li>Evaluate condition expressions — skip if false</li>
     *   <li>Apply DataScope mapping</li>
     *   <li>Render each segment directly via Docxtemplater /render (partial failure mode)</li>
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
                log.debug("Segment '{}' is disabled, skipping", entry.getName());
                continue;
            }

            // Evaluate condition expression
            if (!evaluateCondition(entry.getConditionExpression(), globalData)) {
                log.debug("Segment '{}' condition evaluated to false, skipping", entry.getName());
                SegmentRenderResult skipped = new SegmentRenderResult();
                skipped.setSegmentName(entry.getName());
                skipped.setSuccess(true);
                skipped.setRenderTimeMs(0);
                allResults.add(skipped);
                continue;
            }

            // Apply DataScope
            Map<String, Object> scopedData = resolveDataScope(globalData, entry.getDataScope());

            // Render segment (safe mode — partial failure)
            SegmentRenderResult renderResult = renderSegmentSafe(
                    entry.getFilePath(), entry.getName(), scopedData);
            allResults.add(renderResult);

            if (renderResult.isSuccess() && renderResult.getRenderedBytes() != null) {
                toMerge.add(new RenderedSegmentForMerge(
                        renderResult.getRenderedBytes(), entry.isPageBreakBefore()));
            }
        }

        // All segments skipped
        if (toMerge.isEmpty()) {
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
     * Resolve the data scope for a segment.
     * <p>
     * If dataScope is null or empty, returns globalData as-is (backward compatible).
     * Otherwise, creates a new map containing only the mapped keys:
     * localKey → globalData.get(globalKey).
     * If a globalKey doesn't exist in globalData, logs a warning and sets the value to null.
     *
     * @param globalData the full global data context
     * @param dataScope  the data scope mapping (localKey → globalKey), may be null
     * @return the resolved data map for the segment
     */
    Map<String, Object> resolveDataScope(Map<String, Object> globalData, Map<String, String> dataScope) {
        if (dataScope == null || dataScope.isEmpty()) {
            log.debug("No DataScope configured, passing full global data context");
            return globalData;
        }

        Map<String, Object> scopedData = new HashMap<>();

        for (Map.Entry<String, String> entry : dataScope.entrySet()) {
            String localKey = entry.getKey();
            String globalKey = entry.getValue();

            if (globalData.containsKey(globalKey)) {
                scopedData.put(localKey, globalData.get(globalKey));
            } else {
                log.warn("DataScope mapping: global variable '{}' not found in global data context, setting local variable '{}' to null",
                        globalKey, localKey);
                scopedData.put(localKey, null);
            }
        }

        return scopedData;
    }

    /**
     * Render a single segment safely — catches all exceptions and returns a
     * {@link SegmentRenderResult} with error information (partial failure mode).
     *
     * @param filePath the MinIO file path for the segment template
     * @param name     the segment name
     * @param data     the data context for rendering
     * @return render result with timing and error info
     */
    SegmentRenderResult renderSegmentSafe(String filePath, String name, Map<String, Object> data) {
        SegmentRenderResult result = new SegmentRenderResult();
        result.setSegmentName(name);

        long start = System.currentTimeMillis();
        try {
            byte[] rendered = renderSegment(filePath, data);
            long elapsed = System.currentTimeMillis() - start;
            result.setSuccess(true);
            result.setRenderTimeMs(elapsed);
            result.setRenderedBytes(rendered);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Segment '{}' render failed (partial failure mode): {}", name, e.getMessage());
            result.setSuccess(false);
            result.setRenderTimeMs(elapsed);
            result.setErrorMessage(e.getMessage());
        }
        return result;
    }

    /**
     * Render a segment by calling the Docxtemplater /render endpoint, protected by CircuitBreaker.
     *
     * @param filePath the MinIO file path for the segment template
     * @param data     the data context for rendering
     * @return rendered .docx bytes
     */
    byte[] renderSegment(String filePath, Map<String, Object> data) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("templatePath", filePath);
        requestBody.put("data", data != null ? data : Map.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            return docxtemplaterCb.executeSupplier(() -> {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        docxtemplaterServiceUrl + "/render",
                        HttpMethod.POST, entity, byte[].class);

                if (response.getBody() == null || response.getBody().length == 0) {
                    throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                            "Segment render returned empty result for filePath=" + filePath,
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return response.getBody();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Docxtemplater render call failed for '{}': {}", filePath, e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                    "Segment render service call failed: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("Segment rendering failed for '{}': {}", filePath, e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                    "Segment rendering failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
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
