package com.docgen.property;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblyResult;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.entity.ExpressionType;
import com.docgen.entity.Segment;
import com.docgen.exception.BusinessException;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.service.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.minio.MinioClient;
import net.jqwik.api.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for AssemblyEngineService — Property 6: Conditional Rendering Consistency.
 *
 * <p><b>Validates: Requirements 2.5, 7.6, 8.8</b></p>
 *
 * <p>Verifies that segments with condition expressions evaluated to false do not appear
 * in the merged document, and segments with conditions evaluated to true do appear.</p>
 */
@Tag("Feature: template-segmentation, Property 6: conditionalRenderingConsistency")
class ConditionalRenderingPropertyTest {

    /**
     * Property 6: conditionalRenderingConsistency
     *
     * Generate random segments with boolean condition flags. The ExpressionEngine mock
     * returns the pre-determined boolean for each condition expression.
     * After assembly, verify:
     * - Segments whose condition is true have their marker in the merged document
     * - Segments whose condition is false do NOT have their marker in the merged document
     * - When ALL conditions are false, GENERATE_ALL_SEGMENTS_SKIPPED is thrown
     */
    @Property(tries = 100)
    void conditionalRenderingConsistency(
            @ForAll("conditionalSegmentConfigs") ConditionalConfigInput input
    ) throws Exception {
        // Setup mocks
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        SegmentVersionRepository segmentVersionRepository = mock(SegmentVersionRepository.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test-cond-cb-" + UUID.randomUUID());

        SegmentRendererService rendererService = new SegmentRendererService(
                restTemplate, cb, segmentRepository, segmentVersionRepository,
                mock(MinioClient.class));
        setField(rendererService, "docxtemplaterServiceUrl", "http://localhost:3000");
        setField(rendererService, "bucketName", "docgen-test");

        // Mock segment repository
        for (ConditionalSegmentInfo info : input.segments) {
            Segment seg = new Segment();
            seg.setId(info.id);
            seg.setName("Segment-" + info.id);
            seg.setFilePath("segments/test/" + info.id + ".docx");
            seg.setTenantId(1L);
            when(segmentRepository.findById(info.id)).thenReturn(Optional.of(seg));
        }

        // Mock /render: return unique marker per segment
        when(restTemplate.exchange(
                eq("http://localhost:3000/render"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenAnswer(invocation -> {
            HttpEntity<Map<String, Object>> entity = invocation.getArgument(2);
            Map<String, Object> body = entity.getBody();
            String templatePath = (String) body.get("templatePath");
            String idStr = templatePath.replace("segments/test/", "").replace(".docx", "");
            String marker = "<<CONTENT_" + idStr + ">>";
            return new ResponseEntity<>(marker.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        });

        // Mock /merge-segments: concatenate buffers
        when(restTemplate.exchange(
                eq("http://localhost:3000/merge-segments"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenAnswer(invocation -> {
            HttpEntity<Map<String, Object>> entity = invocation.getArgument(2);
            Map<String, Object> body = entity.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> segments = (List<Map<String, Object>>) body.get("segments");
            StringBuilder merged = new StringBuilder();
            for (Map<String, Object> seg : segments) {
                String buffer = (String) seg.get("buffer");
                merged.append(new String(Base64.getDecoder().decode(buffer), StandardCharsets.UTF_8));
            }
            return new ResponseEntity<>(merged.toString().getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        });

        // Mock ExpressionEngine: return the pre-determined boolean for each condition
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);
        for (ConditionalSegmentInfo info : input.segments) {
            if (info.conditionExpression != null) {
                when(expressionEngine.evaluate(eq(info.conditionExpression), eq(ExpressionType.JAVASCRIPT), anyMap()))
                        .thenReturn(info.conditionResult);
            }
        }

        SegmentDataScopeService dataScopeService = new SegmentDataScopeService();

        AssemblyEngineService assemblyEngine = new AssemblyEngineService(
                rendererService, dataScopeService, expressionEngine, restTemplate, cb);
        setField(assemblyEngine, "docxtemplaterServiceUrl", "http://localhost:3000");

        // Build config
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        List<AssemblySegmentEntry> entries = input.segments.stream().map(info -> {
            AssemblySegmentEntry entry = new AssemblySegmentEntry();
            entry.setSegmentId(info.id);
            entry.setPosition(info.position);
            entry.setEnabled(true);
            entry.setPageBreakBefore(false);
            entry.setConditionExpression(info.conditionExpression);
            return entry;
        }).collect(Collectors.toList());
        config.setSegments(entries);

        // Determine expected included/excluded segments
        List<ConditionalSegmentInfo> expectedIncluded = input.segments.stream()
                .filter(s -> s.conditionExpression == null || s.conditionResult)
                .collect(Collectors.toList());
        List<ConditionalSegmentInfo> expectedExcluded = input.segments.stream()
                .filter(s -> s.conditionExpression != null && !s.conditionResult)
                .collect(Collectors.toList());

        if (expectedIncluded.isEmpty()) {
            // All segments skipped — expect GENERATE_ALL_SEGMENTS_SKIPPED
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> assemblyEngine.assembleDocument(config, Map.of()));
            assertEquals("GENERATE_ALL_SEGMENTS_SKIPPED", ex.getErrorCode());
            return;
        }

        // Execute assembly
        AssemblyResult result = assemblyEngine.assembleDocument(config, Map.of());
        String mergedContent = new String(result.getDocumentBytes(), StandardCharsets.UTF_8);

        // Verify: included segments appear in merged document
        for (ConditionalSegmentInfo info : expectedIncluded) {
            String marker = "<<CONTENT_" + info.id + ">>";
            assertTrue(mergedContent.contains(marker),
                    "Segment " + info.id + " (condition=true) should appear in merged document");
        }

        // Verify: excluded segments do NOT appear in merged document
        for (ConditionalSegmentInfo info : expectedExcluded) {
            String marker = "<<CONTENT_" + info.id + ">>";
            assertFalse(mergedContent.contains(marker),
                    "Segment " + info.id + " (condition=false) should NOT appear in merged document");
        }
    }

    // ── Helper types ──

    static class ConditionalSegmentInfo {
        final long id;
        final int position;
        final String conditionExpression;
        final boolean conditionResult;

        ConditionalSegmentInfo(long id, int position, String conditionExpression, boolean conditionResult) {
            this.id = id;
            this.position = position;
            this.conditionExpression = conditionExpression;
            this.conditionResult = conditionResult;
        }

        @Override
        public String toString() {
            return "Segment{id=" + id + ", pos=" + position
                    + ", expr=" + conditionExpression + ", result=" + conditionResult + "}";
        }
    }

    static class ConditionalConfigInput {
        final List<ConditionalSegmentInfo> segments;

        ConditionalConfigInput(List<ConditionalSegmentInfo> segments) {
            this.segments = segments;
        }

        @Override
        public String toString() {
            return "ConditionalConfigInput{segments=" + segments + "}";
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<ConditionalConfigInput> conditionalSegmentConfigs() {
        return Arbitraries.integers().between(2, 6).flatMap(count -> {
            // Generate segments with random condition flags
            Arbitrary<List<ConditionalSegmentInfo>> segmentsArb = Arbitraries.of(true, false)
                    .list().ofSize(count)
                    .flatMap(hasConditions -> {
                        return Arbitraries.of(true, false).list().ofSize(count).map(conditionResults -> {
                            List<ConditionalSegmentInfo> segments = new ArrayList<>();
                            for (int i = 0; i < count; i++) {
                                String condExpr = hasConditions.get(i)
                                        ? "data.include_" + (i + 1)
                                        : null;
                                boolean condResult = conditionResults.get(i);
                                segments.add(new ConditionalSegmentInfo(
                                        i + 1, i, condExpr, condResult));
                            }
                            return segments;
                        });
                    });

            return segmentsArb.map(ConditionalConfigInput::new);
        });
    }

    // ── Utility ──

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
