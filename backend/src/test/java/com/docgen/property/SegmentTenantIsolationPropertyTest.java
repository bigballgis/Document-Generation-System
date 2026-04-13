package com.docgen.property;

import com.docgen.dto.SegmentDTO;
import com.docgen.dto.SegmentQueryRequest;
import com.docgen.entity.Segment;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentTagMappingRepository;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.service.AuditLogService;
import com.docgen.service.DependencyGraphService;
import com.docgen.service.SegmentService;
import com.docgen.service.SegmentVersionService;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SegmentService — Property 5: Tenant Isolation.
 *
 * <p><b>Validates: Requirements 1.3, 2.10</b></p>
 *
 * <p>Verifies that Segment data is fully isolated between tenants.
 * When querying segments under tenant A's context, no segments belonging
 * to tenant B should appear in the results.</p>
 */
@Tag("Feature: template-segmentation, Property 5: segmentTenantIsolation")
class SegmentTenantIsolationPropertyTest {

    private static final String[] SEGMENT_TYPES = {
            "COVER", "TOC", "CHAPTER", "TABLE", "SIGNATURE", "LEGAL", "APPENDIX"
    };

    /**
     * Property 5: segmentTenantIsolation — query isolation.
     *
     * For any set of segments belonging to multiple tenants, when listing
     * segments under a specific tenant context, the result must contain
     * ONLY segments belonging to that tenant. No cross-tenant data leakage.
     *
     * Formal: ∀ tenantA, tenantB ∈ Tenants where tenantA ≠ tenantB,
     *         query(segments, tenantA) ∩ query(segments, tenantB) = ∅
     */
    @Property(tries = 100)
    void listSegmentsShouldOnlyReturnCurrentTenantSegments(
            @ForAll("multiTenantSegmentData") MultiTenantData data
    ) throws Exception {
        // Setup mocks
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        SegmentTagMappingRepository tagMappingRepository = mock(SegmentTagMappingRepository.class);
        SegmentVersionRepository versionRepository = mock(SegmentVersionRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        MinioClient minioClient = mock(MinioClient.class);

        SegmentService segmentService = new SegmentService(
                segmentRepository, tagMappingRepository, versionRepository,
                mock(SegmentVersionService.class), mock(DependencyGraphService.class), auditLogService, minioClient);
        Field bucketField = SegmentService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(segmentService, "docgen-test");

        // For each tenant, mock the repository to return only that tenant's segments
        // This simulates the Hibernate @Filter(name = "tenantFilter") + explicit tenantId query
        for (Long tenantId : data.tenantIds) {
            List<Segment> tenantSegments = data.segmentsByTenant.getOrDefault(tenantId, Collections.emptyList());

            when(segmentRepository.findByFilters(
                    eq(tenantId), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(tenantSegments));
        }

        // Verify isolation for each tenant
        for (Long tenantId : data.tenantIds) {
            TenantContext.setCurrentTenantId(tenantId);
            try {
                Page<SegmentDTO> result = segmentService.listSegments(null, Pageable.unpaged());

                // All returned segments must belong to the current tenant
                for (SegmentDTO dto : result.getContent()) {
                    assertEquals(tenantId, dto.getTenantId(),
                            "Segment " + dto.getId() + " has tenantId " + dto.getTenantId()
                                    + " but was returned under tenant context " + tenantId);
                }

                // No segment from other tenants should appear
                Set<Long> otherTenantSegmentIds = data.segmentsByTenant.entrySet().stream()
                        .filter(e -> !e.getKey().equals(tenantId))
                        .flatMap(e -> e.getValue().stream())
                        .map(Segment::getId)
                        .collect(Collectors.toSet());

                Set<Long> returnedIds = result.getContent().stream()
                        .map(SegmentDTO::getId)
                        .collect(Collectors.toSet());

                Set<Long> leakedIds = new HashSet<>(returnedIds);
                leakedIds.retainAll(otherTenantSegmentIds);

                assertTrue(leakedIds.isEmpty(),
                        "Cross-tenant data leakage detected! Tenant " + tenantId
                                + " received segments from other tenants: " + leakedIds);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Property 5: segmentTenantIsolation — result set disjointness.
     *
     * For any two distinct tenants, the sets of segment IDs returned by
     * listSegments must be completely disjoint.
     */
    @Property(tries = 100)
    void segmentResultSetsForDifferentTenantsMustBeDisjoint(
            @ForAll("multiTenantSegmentData") MultiTenantData data
    ) throws Exception {
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        SegmentTagMappingRepository tagMappingRepository = mock(SegmentTagMappingRepository.class);
        SegmentVersionRepository versionRepository = mock(SegmentVersionRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        MinioClient minioClient = mock(MinioClient.class);

        SegmentService segmentService = new SegmentService(
                segmentRepository, tagMappingRepository, versionRepository,
                mock(SegmentVersionService.class), mock(DependencyGraphService.class), auditLogService, minioClient);
        Field bucketField = SegmentService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(segmentService, "docgen-test");

        for (Long tenantId : data.tenantIds) {
            List<Segment> tenantSegments = data.segmentsByTenant.getOrDefault(tenantId, Collections.emptyList());
            when(segmentRepository.findByFilters(
                    eq(tenantId), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(tenantSegments));
        }

        // Collect result sets per tenant
        Map<Long, Set<Long>> resultsByTenant = new HashMap<>();
        for (Long tenantId : data.tenantIds) {
            TenantContext.setCurrentTenantId(tenantId);
            try {
                Page<SegmentDTO> result = segmentService.listSegments(null, Pageable.unpaged());
                Set<Long> ids = result.getContent().stream()
                        .map(SegmentDTO::getId)
                        .collect(Collectors.toSet());
                resultsByTenant.put(tenantId, ids);
            } finally {
                TenantContext.clear();
            }
        }

        // Verify pairwise disjointness
        List<Long> tenantList = new ArrayList<>(data.tenantIds);
        for (int i = 0; i < tenantList.size(); i++) {
            for (int j = i + 1; j < tenantList.size(); j++) {
                Long tenantA = tenantList.get(i);
                Long tenantB = tenantList.get(j);
                Set<Long> idsA = resultsByTenant.getOrDefault(tenantA, Collections.emptySet());
                Set<Long> idsB = resultsByTenant.getOrDefault(tenantB, Collections.emptySet());

                Set<Long> intersection = new HashSet<>(idsA);
                intersection.retainAll(idsB);

                assertTrue(intersection.isEmpty(),
                        "Segment IDs overlap between tenant " + tenantA + " and tenant " + tenantB
                                + ": " + intersection);
            }
        }
    }

    /**
     * Property 5: segmentTenantIsolation — TenantContext propagation.
     *
     * Verifies that SegmentService.listSegments uses TenantContext.getCurrentTenantId()
     * to filter queries, and that the repository is called with the correct tenant ID.
     */
    @Property(tries = 100)
    void listSegmentsShouldPassCorrectTenantIdToRepository(
            @ForAll("tenantIds") Long tenantId
    ) throws Exception {
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        SegmentTagMappingRepository tagMappingRepository = mock(SegmentTagMappingRepository.class);
        SegmentVersionRepository versionRepository = mock(SegmentVersionRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        MinioClient minioClient = mock(MinioClient.class);

        SegmentService segmentService = new SegmentService(
                segmentRepository, tagMappingRepository, versionRepository,
                mock(SegmentVersionService.class), mock(DependencyGraphService.class), auditLogService, minioClient);
        Field bucketField = SegmentService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(segmentService, "docgen-test");

        when(segmentRepository.findByFilters(
                eq(tenantId), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        TenantContext.setCurrentTenantId(tenantId);
        try {
            segmentService.listSegments(null, Pageable.unpaged());

            // Verify the repository was called with the exact tenant ID from TenantContext
            verify(segmentRepository).findByFilters(
                    eq(tenantId), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));

            // Verify it was NOT called with any other tenant ID
            verify(segmentRepository, never()).findByFilters(
                    longThat(id -> !id.equals(tenantId)),
                    any(), any(), any(), any(), any(), any(Pageable.class));
        } finally {
            TenantContext.clear();
        }
    }

    // ── Data holder ──

    static class MultiTenantData {
        final Set<Long> tenantIds;
        final Map<Long, List<Segment>> segmentsByTenant;

        MultiTenantData(Set<Long> tenantIds, Map<Long, List<Segment>> segmentsByTenant) {
            this.tenantIds = tenantIds;
            this.segmentsByTenant = segmentsByTenant;
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<Long> tenantIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<MultiTenantData> multiTenantSegmentData() {
        // Generate 2-5 distinct tenant IDs
        return Arbitraries.integers().between(2, 5).flatMap(tenantCount -> {
            Arbitrary<Set<Long>> tenantIdsArb = Arbitraries.longs().between(1L, 1000L)
                    .set().ofMinSize(tenantCount).ofMaxSize(tenantCount);

            return tenantIdsArb.flatMap(tenantIds -> {
                // For each tenant, generate 0-8 segments
                Arbitrary<Map<Long, List<Segment>>> segmentsArb = Arbitraries.just(new HashMap<Long, List<Segment>>());
                long idCounter = 1L;

                for (Long tenantId : tenantIds) {
                    final long startId = idCounter;
                    segmentsArb = segmentsArb.flatMap(map ->
                        Arbitraries.integers().between(0, 8).flatMap(count -> {
                            List<Segment> segments = new ArrayList<>();
                            for (int i = 0; i < count; i++) {
                                segments.add(createSegment(startId + i, tenantId));
                            }
                            map.put(tenantId, segments);
                            return Arbitraries.just(map);
                        })
                    );
                    idCounter += 10; // leave gaps to avoid ID collisions
                }

                final Arbitrary<Map<Long, List<Segment>>> finalSegmentsArb = segmentsArb;
                return finalSegmentsArb.map(segMap -> new MultiTenantData(tenantIds, segMap));
            });
        });
    }

    private static Segment createSegment(long id, Long tenantId) {
        Segment s = new Segment();
        s.setId(id);
        s.setTenantId(tenantId);
        s.setName("Segment-" + id + "-T" + tenantId);
        s.setDescription("Test segment for tenant " + tenantId);
        s.setFilePath("segments/" + tenantId + "/uuid_segment" + id + ".docx");
        s.setComponent(false);
        s.setSegmentType(SEGMENT_TYPES[(int) (id % SEGMENT_TYPES.length)]);
        s.setCreatedBy(42L);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        return s;
    }
}
