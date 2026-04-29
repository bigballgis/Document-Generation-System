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
import io.minio.ObjectWriteResponse;
import net.jqwik.api.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for template clone independence.
 *
 * <p><b>Validates: Requirements 1.7, 1.8</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 2: 模板克隆独立性")
class TemplateClonePropertyTest {

    private static final String[] OUTPUT_FORMATS = {"WORD", "PDF", "BOTH"};
    private static final String[] STORAGE_STRATEGIES = {"TEMP", "PERSISTENT"};
    private static final String[] STATUSES = {"DRAFT", "ACTIVE", "ARCHIVED"};

    /**
     * Property 2: Template clone independence.
     *
     * For any existing template, cloning should produce an independent copy whose name
     * is the original name + " - 副本", status is DRAFT, and configuration matches the
     * original. Modifying the clone must not affect the original template.
     */
    @Property(tries = 100)
    void clonedTemplateShouldBeIndependentFromOriginal(
            @ForAll("sourceTemplates") Template source,
            @ForAll("updateRequests") UpdateTemplateRequest modification
    ) throws Exception {
        // Setup mocks
        TemplateRepository templateRepository = mock(TemplateRepository.class);
        MinioClient minioClient = mock(MinioClient.class);

        TemplateVersionRepository templateVersionRepository = mock(TemplateVersionRepository.class);
        TemplateTagMappingRepository tagMappingRepository = mock(TemplateTagMappingRepository.class);
        TemplateService templateService = new TemplateService(templateRepository, templateVersionRepository, tagMappingRepository,
                mock(com.docgen.repository.UserRepository.class), mock(com.docgen.repository.TeamRepository.class), minioClient);
        Field bucketField = TemplateService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(templateService, "docgen-test");

        TenantContext.setCurrentTenantId(source.getTenantId());

        try {
            // Track saved entities by ID
            AtomicLong idCounter = new AtomicLong(100L);
            Map<Long, Template> store = new HashMap<>();

            // Store the source template
            store.put(source.getId(), source);

            // Mock MinIO copy
            when(minioClient.copyObject(any())).thenReturn(mock(ObjectWriteResponse.class));

            // Mock version repository for updateTemplate auto-versioning
            when(templateVersionRepository.findMaxVersionNumber(anyLong())).thenReturn(Optional.empty());
            when(templateVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Mock findById to return from store
            when(templateRepository.findById(anyLong())).thenAnswer(inv -> {
                Long id = inv.getArgument(0);
                Template t = store.get(id);
                return Optional.ofNullable(t);
            });

            // Mock save: assign ID and store
            when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
                Template t = inv.getArgument(0);
                if (t.getId() == null) {
                    t.setId(idCounter.getAndIncrement());
                }
                t.setCreatedAt(Instant.now());
                t.setUpdatedAt(Instant.now());
                // Deep-copy into store so later mutations on the entity don't affect stored state
                Template stored = copyTemplate(t);
                store.put(stored.getId(), stored);
                return t;
            });

            // Snapshot original values before clone
            String originalName = source.getName();
            String originalDescription = source.getDescription();
            String originalOutputFormat = source.getOutputFormat();
            String originalStorageStrategy = source.getStorageStrategy();
            boolean originalAsync = source.isAsync();
            boolean originalReviewRequired = source.isReviewRequired();
            Long originalTenantId = source.getTenantId();
            Long originalCreatedBy = source.getCreatedBy();
            String originalStatus = source.getStatus();

            // Act: clone the template
            TemplateDTO cloneDTO = templateService.cloneTemplate(source.getId());

            // Assert: clone name = original name + " - 副本"
            assertEquals(originalName + " - 副本", cloneDTO.getName(),
                    "Clone name should be original name + ' - 副本'");

            // Assert: clone status = DRAFT
            assertEquals("DRAFT", cloneDTO.getStatus(),
                    "Clone status should always be DRAFT");

            // Assert: clone has same configuration as original
            assertEquals(originalDescription, cloneDTO.getDescription(),
                    "Clone description should match original");
            assertEquals(originalOutputFormat, cloneDTO.getOutputFormat(),
                    "Clone outputFormat should match original");
            assertEquals(originalStorageStrategy, cloneDTO.getStorageStrategy(),
                    "Clone storageStrategy should match original");
            assertEquals(originalAsync, cloneDTO.isAsync(),
                    "Clone async flag should match original");
            assertEquals(originalReviewRequired, cloneDTO.isReviewRequired(),
                    "Clone reviewRequired flag should match original");
            assertEquals(originalTenantId, cloneDTO.getTenantId(),
                    "Clone tenantId should match original");
            assertEquals(originalCreatedBy, cloneDTO.getCreatedBy(),
                    "Clone createdBy should match original");

            // Assert: clone has a different ID
            assertNotEquals(source.getId(), cloneDTO.getId(),
                    "Clone should have a different ID from the original");

            // Act: modify the clone
            templateService.updateTemplate(cloneDTO.getId(), modification, null);

            // Assert: original template is unaffected
            TemplateDTO originalAfterModification = templateService.getTemplate(source.getId());

            assertEquals(originalName, originalAfterModification.getName(),
                    "Original name should be unaffected after modifying clone");
            assertEquals(originalDescription, originalAfterModification.getDescription(),
                    "Original description should be unaffected after modifying clone");
            assertEquals(originalOutputFormat, originalAfterModification.getOutputFormat(),
                    "Original outputFormat should be unaffected after modifying clone");
            assertEquals(originalStorageStrategy, originalAfterModification.getStorageStrategy(),
                    "Original storageStrategy should be unaffected after modifying clone");
            assertEquals(originalAsync, originalAfterModification.isAsync(),
                    "Original async flag should be unaffected after modifying clone");
            assertEquals(originalReviewRequired, originalAfterModification.isReviewRequired(),
                    "Original reviewRequired should be unaffected after modifying clone");
            assertEquals(originalStatus, originalAfterModification.getStatus(),
                    "Original status should be unaffected after modifying clone");
        } finally {
            TenantContext.clear();
        }
    }


    @Provide
    Arbitrary<Template> sourceTemplates() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-', '_')
                .ofMinLength(1).ofMaxLength(50)
                .filter(s -> !s.isBlank());

        Arbitrary<String> descriptions = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                        .ofMinLength(0).ofMaxLength(200)
        );

        Arbitrary<String> outputFormats = Arbitraries.of(OUTPUT_FORMATS);
        Arbitrary<String> storageStrategies = Arbitraries.of(STORAGE_STRATEGIES);
        Arbitrary<String> statuses = Arbitraries.of(STATUSES);
        Arbitrary<Boolean> asyncFlags = Arbitraries.of(true, false);
        Arbitrary<Boolean> reviewFlags = Arbitraries.of(true, false);
        Arbitrary<Long> tenantIds = Arbitraries.longs().between(1L, 100L);

        return Combinators.combine(names, descriptions, outputFormats, storageStrategies,
                        statuses, asyncFlags, reviewFlags, tenantIds)
                .as((name, desc, format, strategy, status, async, review, tenantId) -> {
                    Template t = new Template();
                    t.setId(1L);
                    t.setTenantId(tenantId);
                    t.setName(name);
                    t.setDescription(desc);
                    t.setTemplateFilePath("templates/" + tenantId + "/uuid_test.docx");
                    t.setOutputFormat(format);
                    t.setStorageStrategy(strategy);
                    t.setAsync(async);
                    t.setCreatedBy(42L);
                    t.setReviewRequired(review);
                    t.setStatus(status);
                    t.setCreatedAt(Instant.now());
                    t.setUpdatedAt(Instant.now());
                    return t;
                });
    }

    @Provide
    Arbitrary<UpdateTemplateRequest> updateRequests() {
        Arbitrary<String> names = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().numeric().withChars(' ', '-', '_')
                        .ofMinLength(1).ofMaxLength(50)
                        .filter(s -> !s.isBlank())
        );

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


    private Template copyTemplate(Template src) {
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
}

