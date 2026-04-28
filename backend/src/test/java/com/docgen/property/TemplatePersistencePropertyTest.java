package com.docgen.property;

import com.docgen.dto.CreateTemplateRequest;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.Team;
import com.docgen.entity.Template;
import com.docgen.repository.TeamRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateVersionRepository;
import com.docgen.repository.UserRepository;
import com.docgen.service.TemplateService;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import net.jqwik.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for template persistence round-trip consistency.
 *
 * <p><b>Validates: Requirements 1.1, 1.2</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 1: 模板持久化往返一致性")
class TemplatePersistencePropertyTest {

    private static final String[] OUTPUT_FORMATS = {"WORD", "PDF", "BOTH"};
    private static final String[] STORAGE_STRATEGIES = {"TEMP", "PERSISTENT"};

    /**
     * Property 1: Template persistence round-trip consistency.
     *
     * For any valid CreateTemplateRequest, after creating a template and querying it
     * by ID, the returned data should be consistent with the creation request
     * (name, description, outputFormat, storageStrategy, async, reviewRequired, status=DRAFT).
     */
    @Property(tries = 100)
    void templateCreateThenQueryShouldReturnConsistentData(
            @ForAll("validCreateTemplateRequests") CreateTemplateRequest request
    ) throws Exception {
        // Setup mocks
        final Long tenantId = 1L;
        final Long userId = 42L;
        TemplateRepository templateRepository = mock(TemplateRepository.class);
        MinioClient minioClient = mock(MinioClient.class);

        TemplateVersionRepository templateVersionRepository = mock(TemplateVersionRepository.class);
        TemplateTagMappingRepository tagMappingRepository = mock(TemplateTagMappingRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        TeamRepository teamRepository = mock(TeamRepository.class);
        when(teamRepository.findById(anyLong())).thenAnswer(inv -> {
            Team team = new Team();
            team.setId(inv.getArgument(0));
            team.setTenantId(tenantId);
            return Optional.of(team);
        });
        TemplateService templateService = new TemplateService(templateRepository, templateVersionRepository, tagMappingRepository,
                userRepository, teamRepository, minioClient);
        Field bucketField = TemplateService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(templateService, "docgen-test");
        TenantContext.setCurrentTenantId(tenantId);

        try {
            // Mock MinIO upload
            when(minioClient.putObject(any())).thenReturn(mock(ObjectWriteResponse.class));

            // Capture the saved template and simulate DB persistence
            when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
                Template t = inv.getArgument(0);
                t.setId(1L);
                t.setCreatedAt(Instant.now());
                t.setUpdatedAt(Instant.now());
                return t;
            });

            // Mock findById to return the same entity that was saved
            when(templateRepository.findById(1L)).thenAnswer(inv -> {
                // Re-create what the service would have saved
                Template t = new Template();
                t.setId(1L);
                t.setTenantId(tenantId);
                t.setName(request.getName());
                t.setDescription(request.getDescription());
                t.setTemplateFilePath("templates/1/fake_test.docx");
                t.setOutputFormat(request.getOutputFormat() != null ? request.getOutputFormat() : "WORD");
                t.setStorageStrategy(request.getStorageStrategy() != null ? request.getStorageStrategy() : "TEMP");
                t.setAsync(request.isAsync());
                t.setTeamId(request.getTeamId());
                t.setCreatedBy(userId);
                t.setCategoryId(request.getCategoryId());
                t.setReviewRequired(request.isReviewRequired());
                t.setStatus("DRAFT");
                t.setCreatedAt(Instant.now());
                t.setUpdatedAt(Instant.now());
                return Optional.of(t);
            });

            MultipartFile file = new MockMultipartFile("file", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "test content".getBytes());

            // Act: create then query
            TemplateDTO created = templateService.createTemplate(request, file, userId);
            TemplateDTO queried = templateService.getTemplate(created.getId());

            // Assert round-trip consistency
            assertEquals(created.getName(), queried.getName(),
                    "Name should be consistent after round-trip");
            assertEquals(created.getDescription(), queried.getDescription(),
                    "Description should be consistent after round-trip");
            assertEquals(created.getOutputFormat(), queried.getOutputFormat(),
                    "OutputFormat should be consistent after round-trip");
            assertEquals(created.getStorageStrategy(), queried.getStorageStrategy(),
                    "StorageStrategy should be consistent after round-trip");
            assertEquals(created.isAsync(), queried.isAsync(),
                    "Async flag should be consistent after round-trip");
            assertEquals(created.isReviewRequired(), queried.isReviewRequired(),
                    "ReviewRequired flag should be consistent after round-trip");
            assertEquals("DRAFT", queried.getStatus(),
                    "Status should always be DRAFT for newly created templates");
            assertEquals(tenantId, queried.getTenantId(),
                    "TenantId should match the current tenant context");
            assertEquals(userId, queried.getCreatedBy(),
                    "CreatedBy should match the userId used during creation");

            // Verify the created DTO matches the request fields
            assertEquals(request.getName(), created.getName());
            assertEquals(request.getDescription(), created.getDescription());
            String expectedFormat = request.getOutputFormat() != null ? request.getOutputFormat() : "WORD";
            assertEquals(expectedFormat, created.getOutputFormat());
            String expectedStrategy = request.getStorageStrategy() != null ? request.getStorageStrategy() : "TEMP";
            assertEquals(expectedStrategy, created.getStorageStrategy());
            assertEquals(request.isAsync(), created.isAsync());
            assertEquals(request.isReviewRequired(), created.isReviewRequired());
            assertEquals("DRAFT", created.getStatus());
        } finally {
            TenantContext.clear();
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<CreateTemplateRequest> validCreateTemplateRequests() {
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

        Arbitrary<Boolean> asyncFlags = Arbitraries.of(true, false);
        Arbitrary<Boolean> reviewFlags = Arbitraries.of(true, false);

        Arbitrary<Long> teamIds = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.longs().between(1L, 1000L).map(l -> l)
        );

        Arbitrary<Long> categoryIds = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.longs().between(1L, 100L).map(l -> l)
        );

        return Combinators.combine(names, descriptions, outputFormats, storageStrategies,
                        asyncFlags, reviewFlags, teamIds, categoryIds)
                .as((name, desc, format, strategy, async, review, teamId, catId) -> {
                    CreateTemplateRequest req = new CreateTemplateRequest();
                    req.setName(name);
                    req.setDescription(desc);
                    req.setOutputFormat(format);
                    req.setStorageStrategy(strategy);
                    req.setAsync(async);
                    req.setReviewRequired(review);
                    req.setTeamId(teamId);
                    req.setCategoryId(catId);
                    return req;
                });
    }
}
