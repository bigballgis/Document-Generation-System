package com.docgen.property;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblyResult;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.SegmentRenderResult;
import com.docgen.entity.ExpressionType;
import com.docgen.entity.Segment;
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
 * Property-based test for AssemblyEngineService — Property 1: Segment Order Preservation.
 *
 * <p><b>Validates: Requirements 8.1, 8.2</b></p>
 *
 * <p>Verifies that after assembly, the content order in the merged document matches
 * the position order defined in the Assembly_Config.</p>
 */
@Tag("Feature: template-segmentation, Property 1: segmentOrderPreservedAfterAssembly")
class AssemblyOrderPropertyTest {

    /**
     * Property 1: segmentOrderPreservedAfterAssembly
     *
     * Generate a random permutation of segments with distinct positions.
     * After assembly, verify that the merged document contains segment content
     * in the order defined by their position values.
     *
     * Strategy: Each segment renders to a unique marker string. The /merge-segments
     * endpoint concatenates all segment buffers in order. We verify the markers
     * appear in position-sorted order in the merged output.
     */
    @Property(tries = 100)
    void segmentOrderPreservedAfterAssembly(
            @ForAll("randomSegmentConfigs") SegmentConfigInput input
    ) throws Exception {
        // Setup mocks
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        SegmentVersionRepository segmentVersionRepository = mock(SegmentVersionRepository.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        MinioClient minioClient = mock(MinioClient.class);

        CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("test-cb-" + UUID.randomUUID());

        // Create SegmentRendererService
        SegmentRendererService rendererService = new SegmentRendererService(
                restTemplate, cb, segmentRepository, segmentVersionRepository, minioClient);
        setField(rendererService, "docxtemplaterServiceUrl", "http://localhost:3000");
        setField(rendererService, "bucketName", "docgen-test");

        // For each segment, mock the repository to return a Segment entity
        for (SegmentInfo info : input.segments) {
            Segment seg = new Segment();
            seg.setId(info.id);
            seg.setName("Segment-" + info.id);
            seg.setFilePath("segments/test/" + info.id + ".docx");
            seg.setTenantId(1L);
            when(segmentRepository.findById(info.id)).thenReturn(Optional.of(seg));
        }

        // Mock /render: return a unique marker for each segment based on its ID
        when(restTemplate.exchange(
                eq("http://localhost:3000/render"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenAnswer(invocation -> {
            HttpEntity<Map<String, Object>> entity = invocation.getArgument(2);
            Map<String, Object> body = entity.getBody();
            String templatePath = (String) body.get("templatePath");
            // Extract segment ID from path: "segments/test/{id}.docx"
            String idStr = templatePath.replace("segments/test/", "").replace(".docx", "");
            String marker = "<<SEGMENT_" + idStr + ">>";
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

        // Create services
        SegmentDataScopeService dataScopeService = new SegmentDataScopeService();
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);

        AssemblyEngineService assemblyEngine = new AssemblyEngineService(
                rendererService, dataScopeService, expressionEngine, restTemplate, cb);
        setField(assemblyEngine, "docxtemplaterServiceUrl", "http://localhost:3000");

        // Build AssemblyConfigDTO from input
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        List<AssemblySegmentEntry> entries = input.segments.stream().map(info -> {
            AssemblySegmentEntry entry = new AssemblySegmentEntry();
            entry.setSegmentId(info.id);
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
            String marker = "<<SEGMENT_" + info.id + ">>";
            int idx = mergedContent.indexOf(marker);
            assertTrue(idx >= 0, "Marker for segment " + info.id + " not found in merged document");
            assertTrue(idx > lastIndex,
                    "Segment " + info.id + " (position " + info.position + ") appears before a segment with lower position");
            lastIndex = idx;
        }
    }

    // ── Helper types ──

    static class SegmentInfo {
        final long id;
        final int position;

        SegmentInfo(long id, int position) {
            this.id = id;
            this.position = position;
        }

        @Override
        public String toString() {
            return "Segment{id=" + id + ", pos=" + position + "}";
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
            // Generate 'count' segments with unique IDs and unique positions
            List<Long> ids = new ArrayList<>();
            List<Integer> positions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ids.add((long) (i + 1));
                positions.add(i);
            }

            // Shuffle positions to create random ordering
            return Arbitraries.shuffle(positions).map(shuffledPositions -> {
                List<SegmentInfo> segments = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    segments.add(new SegmentInfo(ids.get(i), shuffledPositions.get(i)));
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
