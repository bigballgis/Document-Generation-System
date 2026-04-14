package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.MigrationResultDTO;
import com.docgen.entity.*;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.*;
import com.docgen.util.TenantContext;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for migrating traditional single-file templates to Composite_Template format.
 * Handles the full migration workflow: read original .docx → create Segment → create
 * Composite_Template with Assembly_Config → migrate data sources/expressions/variable
 * bindings → archive original template → record audit log.
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final TemplateRepository templateRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final AuditLogService auditLogService;
    private final MinioClient minioClient;
    private final DataSourceRepository dataSourceRepository;
    private final ExpressionRepository expressionRepository;
    private final TemplateVariableRepository templateVariableRepository;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public MigrationService(TemplateRepository templateRepository,
                            AssemblyConfigService assemblyConfigService,
                            AuditLogService auditLogService,
                            MinioClient minioClient,
                            DataSourceRepository dataSourceRepository,
                            ExpressionRepository expressionRepository,
                            TemplateVariableRepository templateVariableRepository) {
        this.templateRepository = templateRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.auditLogService = auditLogService;
        this.minioClient = minioClient;
        this.dataSourceRepository = dataSourceRepository;
        this.expressionRepository = expressionRepository;
        this.templateVariableRepository = templateVariableRepository;
    }

    /**
     * Migrate a traditional single-file template to a Composite_Template.
     *
     * <ol>
     *   <li>Read the original template's .docx file from MinIO</li>
     *   <li>Create a single Segment with the original .docx content</li>
     *   <li>Create a new Composite_Template with Assembly_Config referencing the Segment</li>
     *   <li>Migrate data sources, expressions, and variable bindings to the new template</li>
     *   <li>Archive the original template</li>
     *   <li>Record audit log</li>
     * </ol>
     *
     * @param templateId the ID of the single-file template to migrate
     * @param userId     the ID of the user performing the migration
     * @return migration result containing IDs and counts
     * @throws BusinessException if the original .docx file cannot be read
     */
    @Transactional
    public MigrationResultDTO migrateToComposite(Long templateId, Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        // 1. Load and validate the original template
        Template original = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "Template not found: " + templateId));

        // 2. Read the original .docx file from MinIO
        byte[] docxBytes = readTemplateFile(original.getTemplateFilePath());

        // 3. Upload the .docx as a segment file in MinIO (inline mode, no Segment entity)
        String segmentFilePath = uploadSegmentFile(docxBytes, tenantId, original.getName());

        // 4. Create the Composite_Template
        Template composite = new Template();
        composite.setTenantId(tenantId);
        composite.setName(original.getName() + " (Composite)");
        composite.setDescription(original.getDescription());
        composite.setTemplateType("COMPOSITE");
        composite.setStatus("DRAFT");
        composite.setCreatedBy(userId);
        composite.setTemplateFilePath("");
        composite.setOutputFormat(original.getOutputFormat());
        composite.setTeamId(original.getTeamId());
        composite.setCategoryId(original.getCategoryId());
        composite.setReviewRequired(original.isReviewRequired());
        composite.setAllowHistoryVersions(original.isAllowHistoryVersions());

        // Build Assembly_Config with inline segment data
        AssemblyConfigDTO assemblyConfig = new AssemblyConfigDTO();
        AssemblySegmentEntry entry = new AssemblySegmentEntry();
        entry.setFilePath(segmentFilePath);
        entry.setName(original.getName());
        entry.setSegmentType("CHAPTER");
        entry.setPosition(0);
        entry.setEnabled(true);
        entry.setPageBreakBefore(false);
        List<AssemblySegmentEntry> segments = new ArrayList<>();
        segments.add(entry);
        assemblyConfig.setSegments(segments);
        composite.setAssemblyConfig(assemblyConfigService.serialize(assemblyConfig));

        Template savedComposite = templateRepository.save(composite);

        // 5. Migrate data sources, expressions, and variable bindings
        int migratedDataSources = migrateDataSources(templateId, savedComposite.getId());
        int migratedExpressions = migrateExpressions(templateId, savedComposite.getId());
        int migratedVariableBindings = migrateVariableBindings(templateId, savedComposite.getId());

        // 6. Archive the original template (bypass state machine for migration)
        original.setStatus(TemplateState.ARCHIVED.name());
        templateRepository.save(original);

        // 7. Record audit log
        String details = String.format(
                "{\"sourceTemplateId\":%d,\"compositeTemplateId\":%d,"
                        + "\"migratedDataSources\":%d,\"migratedExpressions\":%d,\"migratedVariableBindings\":%d}",
                templateId, savedComposite.getId(),
                migratedDataSources, migratedExpressions, migratedVariableBindings);
        auditLogService.log(tenantId, userId, "TEMPLATE_MIGRATED",
                "TEMPLATE", savedComposite.getId(), details, null);

        log.info("Template migrated: sourceId={}, compositeId={}, tenantId={}",
                templateId, savedComposite.getId(), tenantId);

        // 8. Build result
        MigrationResultDTO result = new MigrationResultDTO();
        result.setCompositeTemplateId(savedComposite.getId());
        result.setMigratedDataSources(migratedDataSources);
        result.setMigratedExpressions(migratedExpressions);
        result.setMigratedVariableBindings(migratedVariableBindings);
        result.setArchivedOriginalTemplateId(templateId);
        return result;
    }

    private byte[] readTemplateFile(String filePath) {
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filePath)
                        .build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to read template file from MinIO: {}", filePath, e);
            throw new BusinessException(ErrorCode.MIGRATION_FILE_ACCESS_FAILED,
                    "Failed to read original template file: " + filePath,
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String uploadSegmentFile(byte[] content, Long tenantId, String originalName) {
        String uuid = UUID.randomUUID().toString();
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filePath = "segments/" + tenantId + "/" + uuid + "_" + safeName + ".docx";
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filePath)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            .build());
            return filePath;
        } catch (Exception e) {
            log.error("Failed to upload segment file to MinIO: {}", filePath, e);
            throw new BusinessException(ErrorCode.MIGRATION_FILE_ACCESS_FAILED,
                    "Failed to upload segment file during migration",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private int migrateDataSources(Long sourceTemplateId, Long targetTemplateId) {
        List<DataSource> sources = dataSourceRepository.findByTemplateIdOrderByPriorityDesc(sourceTemplateId);
        for (DataSource source : sources) {
            DataSource copy = new DataSource();
            copy.setTemplateId(targetTemplateId);
            copy.setName(source.getName());
            copy.setType(source.getType());
            copy.setConfigJson(source.getConfigJson());
            copy.setCacheEnabled(source.isCacheEnabled());
            copy.setCacheTtl(source.getCacheTtl());
            copy.setPriority(source.getPriority());
            dataSourceRepository.save(copy);
        }
        log.debug("Migrated {} data sources from template {} to {}", sources.size(), sourceTemplateId, targetTemplateId);
        return sources.size();
    }

    private int migrateExpressions(Long sourceTemplateId, Long targetTemplateId) {
        List<Expression> expressions = expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(sourceTemplateId);
        for (Expression expr : expressions) {
            Expression copy = new Expression();
            copy.setTemplateId(targetTemplateId);
            copy.setName(expr.getName());
            copy.setExpressionType(expr.getExpressionType());
            copy.setExpressionText(expr.getExpressionText());
            copy.setDescription(expr.getDescription());
            copy.setExecutionOrder(expr.getExecutionOrder());
            expressionRepository.save(copy);
        }
        log.debug("Migrated {} expressions from template {} to {}", expressions.size(), sourceTemplateId, targetTemplateId);
        return expressions.size();
    }

    private int migrateVariableBindings(Long sourceTemplateId, Long targetTemplateId) {
        List<TemplateVariable> variables = templateVariableRepository.findByTemplateIdOrderByNameAsc(sourceTemplateId);
        for (TemplateVariable var : variables) {
            TemplateVariable copy = new TemplateVariable();
            copy.setTemplateId(targetTemplateId);
            copy.setName(var.getName());
            copy.setVariableType(var.getVariableType());
            copy.setDefaultValue(var.getDefaultValue());
            copy.setDescription(var.getDescription());
            copy.setBindingSource(var.getBindingSource());
            copy.setBindingField(var.getBindingField());
            copy.setBound(var.isBound());
            templateVariableRepository.save(copy);
        }
        log.debug("Migrated {} variable bindings from template {} to {}", variables.size(), sourceTemplateId, targetTemplateId);
        return variables.size();
    }
}
