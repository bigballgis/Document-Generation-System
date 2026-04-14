package com.docgen.property;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblyResult;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.service.AssemblyEngineService;
import com.docgen.service.ExpressionEngine;
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
 * Property-based test for AssemblyEngineService — Property 4: Segment Order Preservation.
 *
 * <p><b>Validates: Requirements 3.4, 3.6, 11.14</b></p>
 *
 * <p>Verifies that after assembly, the content order in the merged document matches
 * the position order defined in the Assembly_Config with inline segment entries.</p>
 */
@Tag("Feature: remove-segment-library, Property 4: assemblyEngineSegmentOrderAndConditionFiltering")
class AssemblyOrderPropertyTest {

    /**
     * Property 4: assemblyEngineSegmentOrderAndConditionFiltering
     *
     * Generate a random permutation of segments with distinct positions and inline filePath/name.
     * After assembly, verify that the merged document contains segment content
     * in the order defined by their position values.
     */
    @Property(tries = 100)
    void segmentOrderPreservedAfterAssembly(
            @ForAll("randomSegmentConfigs") SegmentConfigInput input
    ) throws Exception {
        // Setup mocks
        RestTemplate restTemplate = mock(RestTemplate.class);
        MinioClient minioClient = mock(MinioClient.class);

        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test-cb-" + UUID.randomUUID());

        // Mock /render: return a unique marker for each segment based on its filePath
        when(restTemplate.exchange(
                eq("http://localhost:3000/render"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenAnswer(invocation -> {
            HttpEntity<Map<String, Object>> entity = invocation.getArgument(2);
            Map<String, Object> body = entity.getBody();
            String templatePath = (String) body.get("templatePath");
            String marker = "<<SEGMENT_" + templatePath + ">>";
            return new ResponseEntity<>(marker.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
        });

        // Mock /merge-segments: concatenate all segment buffers in order
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

        // Create services — no more SegmentRendererService or SegmentDataScopeService
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);

        AssemblyEngineService assemblyEngine = new AssemblyEngineService(
                expressionEngine, restTemplate, cb, minioClient);
        setField(assemblyEngine, "docxtemplaterServiceUrl", "http://localhost:3000");
        setField(assemblyEngine, "bucketName", "docgen-test");

        // Build AssemblyConfigDTO from input with inline filePath/name
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        List<AssemblySegmentEntry> entries = input.segments.stream().map(info -> {
            AssemblySegmentEntry entry = new AssemblySegmentEntry();
            entry.setFilePath(info.filePath);
            entry.setName(info.name);
            entry.setPosition(info.position);
            entry.setEnabled(true);
            entry.setPageBreakBefore(false);
            return entry;
        }).collect(Collectors.toList());
        config.setSegments(entries);

        // Execute assembly
        AssemblyResult result = assemblyEngine.assembleDocument(config, Map.of());

        // Verify: content order matches position order
        String mergedContent = new String(result.getDocumentBytes(), StandardCharsets.UTF_8);

        // Sort segments by position to get expected order
        List<SegmentInfo> sortedByPosition = input.segments.stream()
                .sorted(Comparator.comparingInt(s -> s.position))
                .collect(Collectors.toList());

        // Verify each marker appears in the correct order
        int lastIndex = -1;
        for (SegmentInfo info : sortedByPosition) {
            String marker = "<<SEGMENT_" + info.filePath + ">>";
            int idx = mergedContent.indexOf(marker);
            assertTrue(idx >= 0, "Marker for segment '" + info.name + "' not found in merged document");
            assertTrue(idx > lastIndex,
                    "Segment '" + info.name + "' (position " + info.position + ") appears before a segment with lower position");
            lastIndex = idx;
        }
    }

    // ── Helper types ──

    static class SegmentInfo {
        final String filePath;
        final String name;
        final int position;

        SegmentInfo(String filePath, String name, int position) {
            this.filePath = filePath;
            this.name = name;
            this.position = position;
        }

        @Override
        public String toString() {
            return "Segment{filePath='" + filePath + "', name='" + name + "', pos=" + position + "}";
        }
    }

    static class SegmentConfigInput {
        final List<SegmentInfo> segments;

        SegmentConfigInput(List<SegmentInfo> segments) {
            this.segments = segments;
        }

        @Override
        public String toString() {
            return "SegmentConfigInput{segments=" + segments + "}";
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<SegmentConfigInput> randomSegmentConfigs() {
        return Arbitraries.integers().between(2, 8).flatMap(count -> {
            List<Integer> positions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                positions.add(i);
            }

            // Shuffle positions to create random ordering
            return Arbitraries.shuffle(positions).map(shuffledPositions -> {
                List<SegmentInfo> segments = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String filePath = "segments/test/" + (i + 1) + ".docx";
                    String name = "Segment-" + (i + 1);
                    segments.add(new SegmentInfo(filePath, name, shuffledPositions.get(i)));
                }
                return new SegmentConfigInput(segments);
            });
        });
    }

    // ── Utility ──

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
