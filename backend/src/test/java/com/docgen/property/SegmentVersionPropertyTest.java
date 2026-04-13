package com.docgen.property;

import com.docgen.dto.SegmentDTO;
import com.docgen.dto.SegmentVersionDTO;
import com.docgen.entity.Segment;
import com.docgen.entity.SegmentVersion;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.service.AuditLogService;
import com.docgen.service.DependencyGraphService;
import com.docgen.service.SegmentVersionService;
import io.minio.MinioClient;
import net.jqwik.api.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SegmentVersionService — Property 3: Version Number Monotonicity.
 *
 * <p><b>Validates: Requirements 4.1, 4.4</b></p>
 *
 * <p>Verifies that for any sequence of createVersion and rollbackToVersion operations
 * on the same Segment, the version number sequence is strictly monotonically increasing.
 * Rollback creates a NEW version with an incremented number (not reverting the number).</p>
 */
@Tag("Feature: template-segmentation, Property 3: segmentVersionMonotonicallyIncreasing")
class SegmentVersionPropertyTest {

    /**
     * Represents an operation to perform on a segment: either a save (createVersion)
     * or a rollback (rollbackToVersion targeting a previous version).
     */
    enum OpType { SAVE, ROLLBACK }

    /**
     * Property 3: segmentVersionMonotonicallyIncreasing
     *
     * For any random sequence of save and rollback operations on the same Segment,
     * the resulting version numbers must be strictly monotonically increasing.
     * Each operation (including rollback) produces a new version with number = max + 1.
     */
    @Property(tries = 100)
    void versionNumbersShouldBeStrictlyIncreasingAcrossSavesAndRollbacks(
            @ForAll("operationSequences") List<OpType> operations
    ) throws Exception {
        // Need at least 2 operations (first must be SAVE to have something to rollback to)
        Assume.that(operations.size() >= 2);
        Assume.that(operations.get(0) == OpType.SAVE);

        // Setup mocks
        SegmentVersionRepository segmentVersionRepository = mock(SegmentVersionRepository.class);
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        MinioClient minioClient = mock(MinioClient.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        SegmentVersionService service = new SegmentVersionService(
                segmentVersionRepository, segmentRepository, minioClient, auditLogService,
                mock(DependencyGraphService.class));
        Field bucketField = SegmentVersionService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen-test");

        Long segmentId = 1L;
        Long tenantId = 10L;
        Long userId = 42L;

        // Create the segment entity
        Segment segment = new Segment();
        segment.setId(segmentId);
        segment.setTenantId(tenantId);
        segment.setName("TestSegment");
        segment.setFilePath("segments/10/initial.docx");
        segment.setCreatedBy(userId);
        segment.setCreatedAt(Instant.now());
        segment.setUpdatedAt(Instant.now());

        // In-memory version store to simulate DB
        List<SegmentVersion> versionStore = new ArrayList<>();
        AtomicLong versionIdCounter = new AtomicLong(1L);

        // Mock segmentRepository.findById
        when(segmentRepository.findById(segmentId)).thenReturn(Optional.of(segment));
        when(segmentRepository.existsById(segmentId)).thenReturn(true);
        when(segmentRepository.save(any(Segment.class))).thenAnswer(inv -> {
            Segment s = inv.getArgument(0);
            segment.setFilePath(s.getFilePath());
            segment.setUpdatedAt(Instant.now());
            return segment;
        });

        // Mock findMaxVersionNumberBySegmentId — returns max from in-memory store
        when(segmentVersionRepository.findMaxVersionNumberBySegmentId(segmentId)).thenAnswer(inv ->
                versionStore.stream()
                        .filter(v -> v.getSegmentId().equals(segmentId))
                        .mapToInt(SegmentVersion::getVersionNumber)
                        .max()
                        .stream().boxed().findFirst()
                        .map(Optional::of)
                        .orElse(Optional.empty())
        );

        // Mock save for SegmentVersion
        when(segmentVersionRepository.save(any(SegmentVersion.class))).thenAnswer(inv -> {
            SegmentVersion v = inv.getArgument(0);
            v.setId(versionIdCounter.getAndIncrement());
            v.setCreatedAt(Instant.now());
            versionStore.add(copyVersion(v));
            return v;
        });

        // Mock findByIdAndSegmentId — lookup from in-memory store
        when(segmentVersionRepository.findByIdAndSegmentId(anyLong(), eq(segmentId))).thenAnswer(inv -> {
            Long vId = inv.getArgument(0);
            return versionStore.stream()
                    .filter(v -> v.getId().equals(vId) && v.getSegmentId().equals(segmentId))
                    .findFirst()
                    .map(SegmentVersionPropertyTest::copyVersion);
        });

        // Mock MinIO copyObject (no-op for unit test)
        when(minioClient.copyObject(any())).thenReturn(null);

        // ── Execute the operation sequence ──
        List<Integer> observedVersionNumbers = new ArrayList<>();

        for (OpType op : operations) {
            if (op == OpType.SAVE) {
                String filePath = "segments/10/file_" + UUID.randomUUID() + ".docx";
                SegmentVersionDTO result = service.createVersion(segmentId, filePath, userId);
                observedVersionNumbers.add(result.getVersionNumber());
            } else {
                // ROLLBACK: pick a random existing version to rollback to
                if (versionStore.isEmpty()) {
                    // Skip rollback if no versions exist yet
                    continue;
                }
                // Pick the first version as rollback target (any valid version works)
                SegmentVersion target = versionStore.get(0);
                SegmentDTO result = service.rollbackToVersion(segmentId, target.getId(), userId);
                // The rollback creates a new version — get its number from the store
                SegmentVersion latestVersion = versionStore.get(versionStore.size() - 1);
                observedVersionNumbers.add(latestVersion.getVersionNumber());
            }
        }

        // ── Verify strict monotonic increase ──
        assertTrue(observedVersionNumbers.size() >= 2,
                "Should have at least 2 version numbers to verify monotonicity");

        for (int i = 1; i < observedVersionNumbers.size(); i++) {
            assertTrue(observedVersionNumbers.get(i) > observedVersionNumbers.get(i - 1),
                    String.format("Version numbers must be strictly increasing: v[%d]=%d should be > v[%d]=%d",
                            i, observedVersionNumbers.get(i), i - 1, observedVersionNumbers.get(i - 1)));
        }

        // Verify version numbers are consecutive (1, 2, 3, ..., N)
        for (int i = 0; i < observedVersionNumbers.size(); i++) {
            assertEquals(i + 1, observedVersionNumbers.get(i),
                    "Version number at index " + i + " should be " + (i + 1));
        }

        // Verify total versions in store matches observed count
        assertEquals(observedVersionNumbers.size(), versionStore.size(),
                "Version store size should match number of operations executed");
    }

    // ── Generators ──

    @Provide
    Arbitrary<List<OpType>> operationSequences() {
        // First operation is always SAVE, followed by random mix of SAVE and ROLLBACK
        Arbitrary<OpType> ops = Arbitraries.of(OpType.SAVE, OpType.ROLLBACK);
        return ops.list().ofMinSize(1).ofMaxSize(15).map(tail -> {
            List<OpType> sequence = new ArrayList<>();
            sequence.add(OpType.SAVE); // ensure first op is SAVE
            sequence.addAll(tail);
            return sequence;
        });
    }

    // ── Helpers ──

    private static SegmentVersion copyVersion(SegmentVersion src) {
        SegmentVersion copy = new SegmentVersion();
        copy.setId(src.getId());
        copy.setSegmentId(src.getSegmentId());
        copy.setVersionNumber(src.getVersionNumber());
        copy.setFilePath(src.getFilePath());
        copy.setCreatedBy(src.getCreatedBy());
        copy.setCreatedAt(src.getCreatedAt());
        return copy;
    }
}
