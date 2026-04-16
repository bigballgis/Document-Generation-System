package com.docgen.service;

import com.docgen.dto.TemplateConfigExport;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for template import/export operations.
 * Supports importing .docx files as new templates, exporting templates as .docx,
 * and full config export/import as JSON.
 */
@Service
public class TemplateImportExportService {

    private static final Logger log = LoggerFactory.getLogger(TemplateImportExportService.class);

    /** DOCX magic bytes: PK\x03\x04 (ZIP format) */
    private static final byte[] DOCX_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final TemplateRepository templateRepository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public TemplateImportExportService(TemplateRepository templateRepository,
                                       MinioClient minioClient,
                                       ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Import a .docx file as a new template.
     */
    @Transactional
    public TemplateDTO importFromDocx(MultipartFile file, Long userId) {
        validateDocxFile(file);

        Long tenantId = TenantContext.getCurrentTenantId();
        String filePath = uploadToMinio(file, tenantId);

        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName(extractTemplateName(file.getOriginalFilename()));
        template.setDescription("Imported from " + file.getOriginalFilename());
        template.setTemplateFilePath(filePath);
        template.setCreatedBy(userId);
        template.setStatus("DRAFT");

        Template saved = templateRepository.save(template);
        log.info("Template imported from docx: name={}, id={}", saved.getName(), saved.getId());
        return toDTO(saved);
    }

    /**
     * Export a template as a .docx file.
     */
    @Transactional(readOnly = true)
    public byte[] exportToDocx(Long templateId) {
        Template template = findTemplateOrThrow(templateId);

        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(template.getTemplateFilePath())
                .build())) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            stream.transferTo(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export template docx: templateId={}, error={}", templateId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED,
                    "模板文件导出失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Export template complete configuration as JSON.
     */
    @Transactional(readOnly = true)
    public byte[] exportConfig(Long templateId) {
        Template template = findTemplateOrThrow(templateId);

        TemplateConfigExport config = new TemplateConfigExport();

        // Template metadata
        TemplateConfigExport.TemplateMetadata metadata = new TemplateConfigExport.TemplateMetadata();
        metadata.setName(template.getName());
        metadata.setDescription(template.getDescription());
        metadata.setOutputFormat(template.getOutputFormat());
        metadata.setStorageStrategy(template.getStorageStrategy());
        metadata.setAsync(template.isAsync());
        metadata.setReviewRequired(template.isReviewRequired());
        config.setTemplate(metadata);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
        } catch (Exception e) {
            log.error("Failed to serialize template config: templateId={}", templateId, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED,
                    "模板配置导出失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Import template configuration from JSON.
     */
    @Transactional
    public TemplateDTO importConfig(MultipartFile configFile, Long userId) {
        TemplateConfigExport config = parseAndValidateConfig(configFile);

        Long tenantId = TenantContext.getCurrentTenantId();

        // Create template from config metadata
        TemplateConfigExport.TemplateMetadata meta = config.getTemplate();
        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName(meta.getName());
        template.setDescription(meta.getDescription());
        template.setOutputFormat(meta.getOutputFormat() != null ? meta.getOutputFormat() : "WORD");
        template.setStorageStrategy(meta.getStorageStrategy() != null ? meta.getStorageStrategy() : "TEMP");
        template.setAsync(meta.isAsync());
        template.setReviewRequired(meta.isReviewRequired());
        template.setCreatedBy(userId);
        template.setStatus("DRAFT");
        // Placeholder file path — user should upload actual .docx separately
        template.setTemplateFilePath("imported/" + UUID.randomUUID() + "/placeholder.docx");

        Template saved = templateRepository.save(template);

        log.info("Template config imported: name={}, id={}", saved.getName(), saved.getId());

        return toDTO(saved);
    }

    // ── Validation helpers ──

    /**
     * Validate that the uploaded file is a valid .docx.
     */
    void validateDocxFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "上传文件不能为空", HttpStatus.BAD_REQUEST);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "仅支持 .docx 格式文件", HttpStatus.BAD_REQUEST);
        }

        // Check DOCX magic bytes (ZIP PK header)
        try {
            byte[] header = new byte[4];
            try (InputStream is = file.getInputStream()) {
                int read = is.read(header);
                if (read < 4 || !matchesMagicBytes(header)) {
                    throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                            "文件格式无效，不是有效的 .docx 文件", HttpStatus.BAD_REQUEST);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "文件读取失败", HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * Parse and validate JSON config file.
     */
    TemplateConfigExport parseAndValidateConfig(MultipartFile configFile) {
        if (configFile == null || configFile.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_CONFIG,
                    "配置文件不能为空", HttpStatus.BAD_REQUEST);
        }

        TemplateConfigExport config;
        try {
            config = objectMapper.readValue(configFile.getInputStream(), TemplateConfigExport.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_CONFIG,
                    "配置文件 JSON 格式无效: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }

        // Validate required structure
        List<String> errors = new ArrayList<>();

        if (config.getTemplate() == null) {
            errors.add("缺少 'template' 字段");
        } else {
            if (config.getTemplate().getName() == null || config.getTemplate().getName().isBlank()) {
                errors.add("template.name 不能为空");
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_CONFIG,
                    "配置文件验证失败: " + String.join("; ", errors), HttpStatus.BAD_REQUEST);
        }

        return config;
    }

    // ── Private helpers ──

    private boolean matchesMagicBytes(byte[] header) {
        for (int i = 0; i < DOCX_MAGIC.length; i++) {
            if (header[i] != DOCX_MAGIC[i]) return false;
        }
        return true;
    }

    private String extractTemplateName(String filename) {
        if (filename == null) return "Imported Template";
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
    }

    private String uploadToMinio(MultipartFile file, Long tenantId) {
        String objectName = String.format("templates/%d/%s_%s",
                tenantId, UUID.randomUUID(), file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload imported template to MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "模板文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return objectName;
    }

    private Template findTemplateOrThrow(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));
    }

    private TemplateDTO toDTO(Template template) {
        return new TemplateDTO(
                template.getId(), template.getTenantId(), template.getName(),
                template.getDescription(), template.getTemplateFilePath(),
                template.getOutputFormat(), template.getStorageStrategy(),
                template.isAsync(), template.getTeamId(), template.getCreatedBy(),
                template.getCategoryId(), template.isReviewRequired(),
                template.getStatus(), template.getCreatedAt(), template.getUpdatedAt());
    }
}
