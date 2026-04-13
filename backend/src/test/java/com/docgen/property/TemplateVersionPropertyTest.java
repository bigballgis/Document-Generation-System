package com.docgen.property;

import com.docgen.dto.TemplateDTO;
import com.docgen.dto.UpdateTemplateRequest;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateVersionRepository;
import com.docgen.service.TemplateService;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import net.jqwik.api.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for template version monotonic increment and rollback correctness.
 *
 * <p><b>Validates: Requirements 10.1, 10.2, 10.5, 10.6</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 7: 模板版本单调递增与回滚正确性")
class TemplateVersionPropertyTest {

    private static final String[] OUTPUT_FORMATS = {"WORD", "PDF", "BOTH"};
    private static final String[] STORAGE_STRATEGIES = {"TEMP", "PERSISTENT"};

    /**
     * Property 7: Template version monotonic increment and rollback correctness.
     *
     * For any sequence of N template updates, version numbers should be strictly
     * monotonically increasing (1, 2, ..., N). After rollback to version K, a new
     * version N+1 is created whose content matches version K, and version numbers
     * continue to increment.
     */
    @Property(tries = 50)
    void versionNumbersShouldBeMonotonicallyIncreasingAndRollbackShouldPreserveContent(
            @ForAll("updateSequences") List<UpdateTemplateRequest> updates,
            @ForAll("rollbackTargetIndices") int rollbackTargetIndex
    ) throws Exception {
        // Need at least 2 updates to have a meaningful rollback target
        Assume.that(updates.size() >= 2);

        // Setup mocks
        TemplateRepository templateRepository = mock(TemplateRepository.class);
        TemplateVersionRepository templateVersionRepository = mock(TemplateVersionRepository.class);
        MinioClient minioClient = mock(MinioClient.class);

        TemplateService templateService = new TemplateService(templateRepository, templateVersionRepository, mock(TemplateTagMappingRepository.class), minioClient);
        Field bucketField = TemplateService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(templateService, "docgen-test");

        Long tenantId = 1L;
        Long templateId = 1L;
        TenantContext.setCurrentTenantId(tenantId);

        try {
            // In-memory stores to simulate DB
            Map<Long, Template> templateStore = new HashMap<>();
            List<TemplateVersion> versionStore = new ArrayList<>();
            AtomicLong versionIdCounter = new AtomicLong(1L);

            // Create initial template
            Template initial = new Template();
            initial.setId(templateId);
            initial.setTenantId(tenantId);
            initial.setName("InitialTemplate");
            initial.setDescription("Initial description");
            initial.setTemplateFilePath("templates/1/initial.docx");
            initial.setOutputFormat("WORD");
            initial.setStorageStrategy("TEMP");
            initial.setAsync(false);
            initial.setCreatedBy(42L);
            initial.setReviewRequired(false);
            initial.setStatus("DRAFT");
            initial.setCreatedAt(Instant.now());
            initial.setUpdatedAt(Instant.now());
            templateStore.put(templateId, copyTemplate(initial));

            // Mock findById
            when(templateRepository.findById(templateId)).thenAnswer(inv -> {
                Template t = templateStore.get(templateId);
                return Optional.ofNullable(t).map(TemplateVersionPropertyTest::copyTemplate);
            });

            // Mock save for template
            when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
                Template t = inv.getArgument(0);
                t.setUpdatedAt(Instant.now());
                templateStore.put(t.getId(), copyTemplate(t));
                return t;
            });

            // Mock findMaxVersionNumber
            when(templateVersionRepository.findMaxVersionNumber(templateId)).thenAnswer(inv -> {
                return versionStore.stream()
                        .filter(v -> v.getTemplateId().equals(templateId))
                        .mapToInt(TemplateVersion::getVersionNumber)
                        .max()
                        .stream().boxed().findFirst()
                        .map(Optional::of)
                        .orElse(Optional.empty());
            });

            // Mock save for version
            when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> {
                TemplateVersion v = inv.getArgument(0);
                v.setId(versionIdCounter.getAndIncrement());
                v.setCreatedAt(Instant.now());
                versionStore.add(copyVersion(v));
                return v;
            });

            // Mock findByTemplateIdOrderByVersionNumberDesc
            when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)).thenAnswer(inv -> {
                List<TemplateVersion> result = new ArrayList<>(versionStore);
                result.sort(Comparator.comparingInt(TemplateVersion::getVersionNumber).reversed());
                return result;
            });

            // Mock findByIdAndTemplateId
            when(templateVersionRepository.findByIdAndTemplateId(anyLong(), eq(templateId))).thenAnswer(inv -> {
                Long vId = inv.getArgument(0);
                return versionStore.stream()
                        .filter(v -> v.getId().equals(vId) && v.getTemplateId().equals(templateId))
                        .findFirst()
                        .map(TemplateVersionPropertyTest::copyVersion);
            });

            // ── Phase 1: Apply N sequential updates and verify monotonic version numbers ──
            for (UpdateTemplateRequest update : updates) {
                templateService.updateTemplate(templateId, update, null);
            }

            int n = updates.size();
            assertEquals(n, versionStore.size(),
                    "Number of versions should equal number of updates");

            // Verify version numbers are strictly monotonically increasing: 1, 2, ..., N
            for (int i = 0; i < versionStore.size(); i++) {
                assertEquals(i + 1, versionStore.get(i).getVersionNumber(),
                        "Version number at index " + i + " should be " + (i + 1));
            }

            // Verify each version has complete config (configJson is non-null and non-empty)
            for (TemplateVersion v : versionStore) {
                assertNotNull(v.getConfigJson(), "Version configJson should not be null");
                assertFalse(v.getConfigJson().isEmpty(), "Version configJson should not be empty");
                assertNotNull(v.getTemplateFilePath(), "Version templateFilePath should not be null");
                assertEquals(templateId, v.getTemplateId(), "Version templateId should match");
            }

            // ── Phase 2: Rollback to a target version and verify correctness ──
            // Pick a valid rollback target (1-indexed version, map to 0-indexed list)
            int targetIdx = Math.abs(rollbackTargetIndex) % n;
            TemplateVersion targetVersion = versionStore.get(targetIdx);

            // Snapshot target version content before rollback
            String targetFilePath = targetVersion.getTemplateFilePath();
            String targetConfigJson = targetVersion.getConfigJson();

            // Perform rollback
            templateService.rollbackToVersion(templateId, targetVersion.getId());

            // After rollback, a new version N+1 should be created
            assertEquals(n + 1, versionStore.size(),
                    "Rollback should create one additional version");

            TemplateVersion rollbackVersion = versionStore.get(versionStore.size() - 1);

            // New version number should be N+1 (continues incrementing)
            assertEquals(n + 1, rollbackVersion.getVersionNumber(),
                    "Rollback version number should be N+1, continuing the sequence");

            // New version's templateFilePath should match the target version
            assertEquals(targetFilePath, rollbackVersion.getTemplateFilePath(),
                    "Rollback version templateFilePath should match target version");

            // Verify all version numbers are still strictly monotonically increasing
            for (int i = 0; i < versionStore.size(); i++) {
                assertEquals(i + 1, versionStore.get(i).getVersionNumber(),
                        "After rollback, version number at index " + i + " should be " + (i + 1));
            }

        } finally {
            TenantContext.clear();
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<List<UpdateTemplateRequest>> updateSequences() {
        return updateRequests().list().ofMinSize(2).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Integer> rollbackTargetIndices() {
        return Arbitraries.integers().between(0, 100);
    }

    private Arbitrary<UpdateTemplateRequest> updateRequests() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-', '_')
                .ofMinLength(1).ofMaxLength(50)
                .filter(s -> !s.isBlank());

        Arbitrary<String> descriptions = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                        .ofMinLength(0).ofMaxLength(200)
        );

        Arbitrary<String> outputFormats = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of(OUTPUT_FORMATS)
        );

        Arbitrary<String> storageStrategies = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of(STORAGE_STRATEGIES)
        );

        Arbitrary<Boolean> asyncFlags = Arbitraries.oneOf(
                Arbitraries.just((Boolean) null),
                Arbitraries.of(true, false)
        );

        Arbitrary<Boolean> reviewFlags = Arbitraries.oneOf(
                Arbitraries.just((Boolean) null),
                Arbitraries.of(true, false)
        );

        return Combinators.combine(names, descriptions, outputFormats, storageStrategies,
                        asyncFlags, reviewFlags)
                .as((name, desc, format, strategy, async, review) -> {
                    UpdateTemplateRequest req = new UpdateTemplateRequest();
                    req.setName(name);
                    req.setDescription(desc);
                    req.setOutputFormat(format);
                    req.setStorageStrategy(strategy);
                    req.setAsync(async);
                    req.setReviewRequired(review);
                    return req;
                });
    }

    // ── Helpers ──

    private static Template copyTemplate(Template src) {
        Template copy = new Template();
        copy.setId(src.getId());
        copy.setTenantId(src.getTenantId());
        copy.setName(src.getName());
        copy.setDescription(src.getDescription());
        copy.setTemplateFilePath(src.getTemplateFilePath());
        copy.setOutputFormat(src.getOutputFormat());
        copy.setStorageStrategy(src.getStorageStrategy());
        copy.setAsync(src.isAsync());
        copy.setTeamId(src.getTeamId());
        copy.setCreatedBy(src.getCreatedBy());
        copy.setCategoryId(src.getCategoryId());
        copy.setReviewRequired(src.isReviewRequired());
        copy.setStatus(src.getStatus());
        copy.setCreatedAt(src.getCreatedAt());
        copy.setUpdatedAt(src.getUpdatedAt());
        return copy;
    }

    private static TemplateVersion copyVersion(TemplateVersion src) {
        TemplateVersion copy = new TemplateVersion();
        copy.setId(src.getId());
        copy.setTemplateId(src.getTemplateId());
        copy.setVersionNumber(src.getVersionNumber());
        copy.setTemplateFilePath(src.getTemplateFilePath());
        copy.setConfigJson(src.getConfigJson());
        copy.setCreatedBy(src.getCreatedBy());
        copy.setCreatedAt(src.getCreatedAt());
        return copy;
    }
}
