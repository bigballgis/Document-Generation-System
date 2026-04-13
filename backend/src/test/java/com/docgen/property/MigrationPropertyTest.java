package com.docgen.property;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.MigrationResultDTO;
import com.docgen.entity.*;
import com.docgen.repository.*;
import com.docgen.service.AssemblyConfigService;
import com.docgen.service.AuditLogService;
import com.docgen.service.MigrationService;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import net.jqwik.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for MigrationService — Property 7: Migration Round-Trip Consistency.
 *
 * <p><b>Validates: Requirements 9.4, 9.6</b></p>
 *
 * <p>Verifies that migrating a single-file template to a composite template
 * preserves all configuration: data sources, expressions, and variable bindings.</p>
 */
@Tag("Feature: template-segmentation, Property 7: migrationPreservesConfiguration")
class MigrationPropertyTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final byte[] FAKE_DOCX = new byte[]{0x50, 0x4B, 0x03, 0x04};

    /**
     * Property 7: migrationPreservesConfiguration
     *
     * For any random template with random data sources, expressions, and variable bindings,
     * after migration the new composite template must contain copies of all original
     * configurations with identical field values.
     */
    @Property(tries = 100)
    void migrationPreservesConfiguration(
            @ForAll("templateConfigs") TemplateConfig config
    ) throws Exception {
        // Arrange: set up mocks
        TemplateRepository templateRepository = mock(TemplateRepository.class);
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AssemblyConfigService assemblyConfigService = new AssemblyConfigService(objectMapper, segmentRepository);
        AuditLogService auditLogService = mock(AuditLogService.class);
        MinioClient minioClient = mock(MinioClient.class);
        DataSourceRepository dataSourceRepository = mock(DataSourceRepository.class);
        ExpressionRepository expressionRepository = mock(ExpressionRepository.class);
        TemplateVariableRepository templateVariableRepository = mock(TemplateVariableRepository.class);

        MigrationService service = new MigrationService(
                templateRepository, segmentRepository, assemblyConfigService,
                auditLogService, minioClient,
                dataSourceRepository, expressionRepository, templateVariableRepository);

        // Set bucket name via reflection
        setField(service, "bucketName", "docgen");

        TenantContext.setCurrentTenantId(TENANT_ID);

        try {
            // Build original template
            Template original = new Template();
            original.setId(config.templateId);
            original.setTenantId(TENANT_ID);
            original.setName(config.templateName);
            original.setDescription(config.templateDescription);
            original.setTemplateFilePath("templates/1/test.docx");
            original.setOutputFormat(config.outputFormat);
            original.setStatus("ACTIVE");
            original.setTemplateType("SINGLE");
            original.setCreatedBy(USER_ID);
            original.setCategoryId(config.categoryId);
            original.setTeamId(config.teamId);
            original.setReviewRequired(config.reviewRequired);
            original.setAllowHistoryVersions(config.allowHistoryVersions);

            when(templateRepository.findById(config.templateId)).thenReturn(Optional.of(original));

            // Mock MinIO read
            InputStream mockStream = new ByteArrayInputStream(FAKE_DOCX);
            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(
                    new io.minio.GetObjectResponse(
                            okhttp3.Headers.of(), "docgen", "", "test.docx", mockStream));
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

            // Track saved entities
            List<DataSource> savedDataSources = new ArrayList<>();
            List<Expression> savedExpressions = new ArrayList<>();
            List<TemplateVariable> savedVariables = new ArrayList<>();

            // Mock segment save
            AtomicLong segmentIdCounter = new AtomicLong(100);
            when(segmentRepository.save(any(Segment.class))).thenAnswer(inv -> {
                Segment s = inv.getArgument(0);
                s.setId(segmentIdCounter.getAndIncrement());
                s.setCreatedAt(Instant.now());
                s.setUpdatedAt(Instant.now());
                return s;
            });

            // Mock template save (for composite and archived original)
            AtomicLong templateIdCounter = new AtomicLong(200);
            when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
                Template t = inv.getArgument(0);
                if (t.getId() == null) {
                    t.setId(templateIdCounter.getAndIncrement());
                }
                t.setCreatedAt(Instant.now());
                t.setUpdatedAt(Instant.now());
                return t;
            });

            // Mock data source migration
            when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(config.templateId))
                    .thenReturn(config.dataSources);
            when(dataSourceRepository.save(any(DataSource.class))).thenAnswer(inv -> {
                DataSource ds = inv.getArgument(0);
                savedDataSources.add(ds);
                return ds;
            });

            // Mock expression migration
            when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(config.templateId))
                    .thenReturn(config.expressions);
            when(expressionRepository.save(any(Expression.class))).thenAnswer(inv -> {
                Expression e = inv.getArgument(0);
                savedExpressions.add(e);
                return e;
            });

            // Mock variable binding migration
            when(templateVariableRepository.findByTemplateIdOrderByNameAsc(config.templateId))
                    .thenReturn(config.variables);
            when(templateVariableRepository.save(any(TemplateVariable.class))).thenAnswer(inv -> {
                TemplateVariable v = inv.getArgument(0);
                savedVariables.add(v);
                return v;
            });

            // Act
            MigrationResultDTO result = service.migrateToComposite(config.templateId, USER_ID);

            // Assert: counts match
            assertEquals(config.dataSources.size(), result.getMigratedDataSources(),
                    "All data sources must be migrated");
            assertEquals(config.expressions.size(), result.getMigratedExpressions(),
                    "All expressions must be migrated");
            assertEquals(config.variables.size(), result.getMigratedVariableBindings(),
                    "All variable bindings must be migrated");

            // Assert: data source fields preserved
            for (int i = 0; i < config.dataSources.size(); i++) {
                DataSource orig = config.dataSources.get(i);
                DataSource copy = savedDataSources.get(i);
                assertNotEquals(config.templateId, copy.getTemplateId(),
                        "Migrated data source must reference the new composite template");
                assertEquals(orig.getName(), copy.getName(), "Data source name must be preserved");
                assertEquals(orig.getType(), copy.getType(), "Data source type must be preserved");
                assertEquals(orig.getConfigJson(), copy.getConfigJson(), "Data source config must be preserved");
                assertEquals(orig.isCacheEnabled(), copy.isCacheEnabled(), "Cache enabled must be preserved");
                assertEquals(orig.getCacheTtl(), copy.getCacheTtl(), "Cache TTL must be preserved");
                assertEquals(orig.getPriority(), copy.getPriority(), "Priority must be preserved");
            }

            // Assert: expression fields preserved
            for (int i = 0; i < config.expressions.size(); i++) {
                Expression orig = config.expressions.get(i);
                Expression copy = savedExpressions.get(i);
                assertNotEquals(config.templateId, copy.getTemplateId(),
                        "Migrated expression must reference the new composite template");
                assertEquals(orig.getName(), copy.getName(), "Expression name must be preserved");
                assertEquals(orig.getExpressionType(), copy.getExpressionType(), "Expression type must be preserved");
                assertEquals(orig.getExpressionText(), copy.getExpressionText(), "Expression text must be preserved");
                assertEquals(orig.getDescription(), copy.getDescription(), "Expression description must be preserved");
                assertEquals(orig.getExecutionOrder(), copy.getExecutionOrder(), "Execution order must be preserved");
            }

            // Assert: variable binding fields preserved
            for (int i = 0; i < config.variables.size(); i++) {
                TemplateVariable orig = config.variables.get(i);
                TemplateVariable copy = savedVariables.get(i);
                assertNotEquals(config.templateId, copy.getTemplateId(),
                        "Migrated variable must reference the new composite template");
                assertEquals(orig.getName(), copy.getName(), "Variable name must be preserved");
                assertEquals(orig.getVariableType(), copy.getVariableType(), "Variable type must be preserved");
                assertEquals(orig.getDefaultValue(), copy.getDefaultValue(), "Default value must be preserved");
                assertEquals(orig.getDescription(), copy.getDescription(), "Variable description must be preserved");
                assertEquals(orig.getBindingSource(), copy.getBindingSource(), "Binding source must be preserved");
                assertEquals(orig.getBindingField(), copy.getBindingField(), "Binding field must be preserved");
                assertEquals(orig.isBound(), copy.isBound(), "Bound status must be preserved");
            }

            // Assert: original template archived
            assertEquals("ARCHIVED", original.getStatus(),
                    "Original template must be archived after migration");

            // Assert: result IDs are set
            assertNotNull(result.getCompositeTemplateId(), "Composite template ID must be set");
            assertNotNull(result.getSegmentId(), "Segment ID must be set");
            assertEquals(config.templateId, result.getArchivedOriginalTemplateId(),
                    "Archived original template ID must match source");

        } finally {
            TenantContext.clear();
        }
    }

    // ── Helper types ──

    static class TemplateConfigBase {
        final Long templateId;
        final String templateName;
        final String templateDescription;
        final String outputFormat;
        final Long categoryId;
        final Long teamId;
        final boolean reviewRequired;
        final boolean allowHistoryVersions;

        TemplateConfigBase(Long templateId, String templateName, String templateDescription,
                           String outputFormat, Long categoryId, Long teamId,
                           boolean reviewRequired, boolean allowHistoryVersions) {
            this.templateId = templateId;
            this.templateName = templateName;
            this.templateDescription = templateDescription;
            this.outputFormat = outputFormat;
            this.categoryId = categoryId;
            this.teamId = teamId;
            this.reviewRequired = reviewRequired;
            this.allowHistoryVersions = allowHistoryVersions;
        }
    }

    static class TemplateConfig {
        final Long templateId;
        final String templateName;
        final String templateDescription;
        final String outputFormat;
        final Long categoryId;
        final Long teamId;
        final boolean reviewRequired;
        final boolean allowHistoryVersions;
        final List<DataSource> dataSources;
        final List<Expression> expressions;
        final List<TemplateVariable> variables;

        TemplateConfig(Long templateId, String templateName, String templateDescription,
                       String outputFormat, Long categoryId, Long teamId,
                       boolean reviewRequired, boolean allowHistoryVersions,
                       List<DataSource> dataSources, List<Expression> expressions,
                       List<TemplateVariable> variables) {
            this.templateId = templateId;
            this.templateName = templateName;
            this.templateDescription = templateDescription;
            this.outputFormat = outputFormat;
            this.categoryId = categoryId;
            this.teamId = teamId;
            this.reviewRequired = reviewRequired;
            this.allowHistoryVersions = allowHistoryVersions;
            this.dataSources = dataSources;
            this.expressions = expressions;
            this.variables = variables;
        }

        @Override
        public String toString() {
            return "TemplateConfig{name=" + templateName
                    + ", dataSources=" + dataSources.size()
                    + ", expressions=" + expressions.size()
                    + ", variables=" + variables.size() + "}";
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<TemplateConfig> templateConfigs() {
        Arbitrary<Long> templateIds = Arbitraries.longs().between(1L, 1000L);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> descriptions = Arbitraries.strings().alpha().ofMaxLength(100);
        Arbitrary<String> formats = Arbitraries.of("WORD", "PDF");
        Arbitrary<Boolean> booleans = Arbitraries.of(true, false);

        // First combine the basic template fields (8 params max)
        Arbitrary<TemplateConfigBase> base = Combinators.combine(
                templateIds, names, descriptions, formats,
                Arbitraries.longs().between(1L, 100L),
                Arbitraries.longs().between(1L, 50L),
                booleans, booleans
        ).as(TemplateConfigBase::new);

        // Then combine with the lists
        return Combinators.combine(base, dataSourceLists(), expressionLists(), variableLists())
                .as((b, ds, exprs, vars) -> new TemplateConfig(
                        b.templateId, b.templateName, b.templateDescription,
                        b.outputFormat, b.categoryId, b.teamId,
                        b.reviewRequired, b.allowHistoryVersions,
                        ds, exprs, vars));
    }

    Arbitrary<List<DataSource>> dataSourceLists() {
        Arbitrary<DataSource> single = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.of("HTTP_API", "DATABASE", "STATIC"),
                Arbitraries.integers().between(0, 10),
                Arbitraries.of(true, false),
                Arbitraries.integers().between(60, 3600)
        ).as((name, type, priority, cacheEnabled, cacheTtl) -> {
            DataSource ds = new DataSource();
            ds.setName(name);
            ds.setType(type);
            ds.setConfigJson("{\"url\":\"https://example.com\"}");
            ds.setPriority(priority);
            ds.setCacheEnabled(cacheEnabled);
            ds.setCacheTtl(cacheTtl);
            return ds;
        });
        return single.list().ofMaxSize(5);
    }

    Arbitrary<List<Expression>> expressionLists() {
        Arbitrary<Expression> single = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.of("JAVASCRIPT", "EXCEL"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                Arbitraries.integers().between(0, 10)
        ).as((name, type, text, order) -> {
            Expression e = new Expression();
            e.setName(name);
            e.setExpressionType(type);
            e.setExpressionText(text);
            e.setDescription("desc_" + name);
            e.setExecutionOrder(order);
            return e;
        });
        return single.list().ofMaxSize(5);
    }

    Arbitrary<List<TemplateVariable>> variableLists() {
        Arbitrary<TemplateVariable> single = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.of("STRING", "NUMBER", "BOOLEAN", "DATE"),
                Arbitraries.of(true, false),
                Arbitraries.strings().alpha().ofMaxLength(20).injectNull(0.3),
                Arbitraries.strings().alpha().ofMaxLength(20).injectNull(0.3)
        ).as((name, varType, bound, bindingSource, bindingField) -> {
            TemplateVariable v = new TemplateVariable();
            v.setName(name);
            v.setVariableType(varType);
            v.setBound(bound);
            v.setBindingSource(bindingSource);
            v.setBindingField(bindingField);
            v.setDefaultValue("default_" + name);
            v.setDescription("desc_" + name);
            return v;
        });
        return single.list().ofMaxSize(8);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + name, e);
        }
    }
}
