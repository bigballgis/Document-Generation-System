package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.Segment;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service for importing and exporting Composite_Templates as ZIP archives.
 * <p>
 * ZIP structure:
 * <pre>
 *   config.json          — Assembly_Config + template metadata
 *   segments/
 *     {segmentName}.docx — Each segment's .docx file
 * </pre>
 *
 * <p>Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5</p>
 */
@Service
public class CompositeImportExportService {

    private static final Logger log = LoggerFactory.getLogger(CompositeImportExportService.class);

    private final TemplateRepository templateRepository;
    private final SegmentRepository segmentRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public CompositeImportExportService(TemplateRepository templateRepository,
                                        SegmentRepository segmentRepository,
                                        AssemblyConfigService assemblyConfigService,
                                        MinioClient minioClient,
                                        ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.segmentRepository = segmentRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Export a Composite_Template as a ZIP containing all Segment .docx files + config.json.
     */
    @Transactional(readOnly = true)
    public byte[] exportAsZip(Long compositeTemplateId) {
        Template template = findCompositeTemplateOrThrow(compositeTemplateId);
        AssemblyConfigDTO config = assemblyConfigService.deserialize(template.getAssemblyConfig());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Write config.json
            CompositeExportConfig exportConfig = buildExportConfig(template, config);
            byte[] configBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportConfig);
            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write(configBytes);
            zos.closeEntry();

            // Write each segment .docx
            if (config.getSegments() != null) {
                Set<String> usedNames = new HashSet<>();
                for (AssemblySegmentEntry entry : config.getSegments()) {
                    Segment segment = segmentRepository.findById(entry.getSegmentId()).orElse(null);
                    if (segment == null) {
                        log.warn("Segment {} not found during export, skipping", entry.getSegmentId());
                        continue;
                    }
                    byte[] docxBytes = downloadFromMinio(segment.getFilePath());
                    String fileName = uniqueFileName(segment.getName(), usedNames);
                    zos.putNextEntry(new ZipEntry("segments/" + fileName + ".docx"));
                    zos.write(docxBytes);
                    zos.closeEntry();
                }
            }

            zos.finish();
            return baos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to export composite template as ZIP: templateId={}", compositeTemplateId, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED,
                    "Failed to export composite template as ZIP", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Export only the JSON configuration of a Composite_Template.
     */
    @Transactional(readOnly = true)
    public byte[] exportConfig(Long compositeTemplateId) {
        Template template = findCompositeTemplateOrThrow(compositeTemplateId);
        AssemblyConfigDTO config = assemblyConfigService.deserialize(template.getAssemblyConfig());

        try {
            CompositeExportConfig exportConfig = buildExportConfig(template, config);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportConfig);
        } catch (JsonProcessingException e) {
            log.error("Failed to export config JSON: templateId={}", compositeTemplateId, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED,
                    "Failed to export composite template config", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Import a Composite_Template from a ZIP file.
     * Validates ZIP structure, creates Segments, creates Composite_Template.
     * If a Component_Template with the same name exists, it is linked instead of creating a new Segment.
     */
    @Transactional
    public TemplateDTO importFromZip(MultipartFile zipFile, Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        Map<String, byte[]> segmentFiles = new LinkedHashMap<>();
        CompositeExportConfig exportConfig = null;

        // Parse ZIP
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                byte[] content = zis.readAllBytes();

                if ("config.json".equals(name)) {
                    exportConfig = objectMapper.readValue(content, CompositeExportConfig.class);
                } else if (name.startsWith("segments/") && name.endsWith(".docx")) {
                    String segmentName = name.substring("segments/".length(),
                            name.length() - ".docx".length());
                    segmentFiles.put(segmentName, content);
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("Failed to parse import ZIP file", e);
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "Invalid ZIP file: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }

        // Validate structure
        if (exportConfig == null) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "ZIP must contain config.json", HttpStatus.BAD_REQUEST);
        }
        if (segmentFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "ZIP must contain at least one .docx file in segments/ directory",
                    HttpStatus.BAD_REQUEST);
        }

        // Create segments and build mapping: original segmentName -> new segmentId
        Map<String, Long> nameToSegmentId = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> fileEntry : segmentFiles.entrySet()) {
            String segmentName = fileEntry.getKey();
            byte[] docxBytes = fileEntry.getValue();

            // Check if a Component_Template with the same name exists
            Long existingComponentId = findExistingComponentByName(segmentName, tenantId);
            if (existingComponentId != null) {
                nameToSegmentId.put(segmentName, existingComponentId);
                log.info("Linked existing component '{}' (id={}) during import", segmentName, existingComponentId);
                continue;
            }

            // Create new segment
            String filePath = uploadToMinio(docxBytes, segmentName, tenantId);
            Segment segment = new Segment();
            segment.setTenantId(tenantId);
            segment.setName(segmentName);
            segment.setFilePath(filePath);
            segment.setComponent(false);
            segment.setCreatedBy(userId);
            segment = segmentRepository.save(segment);
            nameToSegmentId.put(segmentName, segment.getId());
        }

        // Build assembly config with new segment IDs
        AssemblyConfigDTO assemblyConfig = rebuildAssemblyConfig(exportConfig, nameToSegmentId);
        String assemblyConfigJson;
        try {
            assemblyConfigJson = objectMapper.writeValueAsString(assemblyConfig);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to serialize assembly config", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        // Create composite template
        String templateName = exportConfig.getTemplateName() != null
                ? exportConfig.getTemplateName() : "Imported Composite Template";
        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName(templateName);
        template.setDescription(exportConfig.getTemplateDescription());
        template.setTemplateFilePath("composite://" + templateName);
        template.setTemplateType("COMPOSITE");
        template.setAssemblyConfig(assemblyConfigJson);
        template.setCreatedBy(userId);
        template.setStatus("DRAFT");
        template = templateRepository.save(template);

        log.info("Imported composite template: name={}, id={}, segments={}",
                template.getName(), template.getId(), nameToSegmentId.size());
        return toTemplateDTO(template);
    }

    // ── Private helpers ──

    private Template findCompositeTemplateOrThrow(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "Template not found: " + templateId, HttpStatus.NOT_FOUND));
        if (!"COMPOSITE".equals(template.getTemplateType())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "Template is not a composite template: " + templateId, HttpStatus.BAD_REQUEST);
        }
        return template;
    }

    private CompositeExportConfig buildExportConfig(Template template, AssemblyConfigDTO config) {
        CompositeExportConfig export = new CompositeExportConfig();
        export.setTemplateName(template.getName());
        export.setTemplateDescription(template.getDescription());

        List<CompositeExportConfig.SegmentExportEntry> entries = new ArrayList<>();
        if (config.getSegments() != null) {
            for (AssemblySegmentEntry entry : config.getSegments()) {
                CompositeExportConfig.SegmentExportEntry exportEntry = new CompositeExportConfig.SegmentExportEntry();
                Segment segment = segmentRepository.findById(entry.getSegmentId()).orElse(null);
                exportEntry.setSegmentName(segment != null ? segment.getName() : "unknown");
                exportEntry.setPosition(entry.getPosition());
                exportEntry.setEnabled(entry.isEnabled());
                exportEntry.setPageBreakBefore(entry.isPageBreakBefore());
                exportEntry.setLockedVersion(entry.getLockedVersion());
                exportEntry.setConditionExpression(entry.getConditionExpression());
                exportEntry.setDataScope(entry.getDataScope());
                exportEntry.setComponent(segment != null && segment.isComponent());
                entries.add(exportEntry);
            }
        }
        export.setSegments(entries);
        return export;
    }

    private Long findExistingComponentByName(String name, Long tenantId) {
        return segmentRepository.findAll().stream()
                .filter(s -> s.getTenantId().equals(tenantId)
                        && s.isComponent()
                        && s.getName().equals(name))
                .map(Segment::getId)
                .findFirst()
                .orElse(null);
    }

    private AssemblyConfigDTO rebuildAssemblyConfig(CompositeExportConfig exportConfig,
                                                     Map<String, Long> nameToSegmentId) {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        List<AssemblySegmentEntry> entries = new ArrayList<>();

        if (exportConfig.getSegments() != null) {
            for (CompositeExportConfig.SegmentExportEntry exportEntry : exportConfig.getSegments()) {
                Long segmentId = nameToSegmentId.get(exportEntry.getSegmentName());
                if (segmentId == null) continue;

                AssemblySegmentEntry entry = new AssemblySegmentEntry();
                entry.setSegmentId(segmentId);
                entry.setPosition(exportEntry.getPosition());
                entry.setEnabled(exportEntry.isEnabled());
                entry.setPageBreakBefore(exportEntry.isPageBreakBefore());
                entry.setLockedVersion(exportEntry.getLockedVersion());
                entry.setConditionExpression(exportEntry.getConditionExpression());
                entry.setDataScope(exportEntry.getDataScope());
                entries.add(entry);
            }
        }
        config.setSegments(entries);
        return config;
    }

    private byte[] downloadFromMinio(String filePath) {
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filePath)
                        .build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", filePath, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED,
                    "Failed to read segment file: " + filePath, HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String uploadToMinio(byte[] content, String segmentName, Long tenantId) {
        String uuid = UUID.randomUUID().toString();
        String objectPath = "segments/" + tenantId + "/" + uuid + "_" + segmentName + ".docx";
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());
            return objectPath;
        } catch (Exception e) {
            log.error("Failed to upload segment file to MinIO: {}", objectPath, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to upload segment file", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String uniqueFileName(String name, Set<String> usedNames) {
        String base = name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff_\\-]", "_");
        String result = base;
        int counter = 1;
        while (usedNames.contains(result)) {
            result = base + "_" + counter++;
        }
        usedNames.add(result);
        return result;
    }

    private TemplateDTO toTemplateDTO(Template template) {
        TemplateDTO dto = new TemplateDTO();
        dto.setId(template.getId());
        dto.setTenantId(template.getTenantId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setTemplateFilePath(template.getTemplateFilePath());
        dto.setOutputFormat(template.getOutputFormat());
        dto.setStorageStrategy(template.getStorageStrategy());
        dto.setAsync(template.isAsync());
        dto.setTeamId(template.getTeamId());
        dto.setCreatedBy(template.getCreatedBy());
        dto.setCategoryId(template.getCategoryId());
        dto.setReviewRequired(template.isReviewRequired());
        dto.setStatus(template.getStatus());
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        return dto;
    }

    /**
     * Internal DTO for the config.json structure in the export ZIP.
     */
    public static class CompositeExportConfig {
        private String templateName;
        private String templateDescription;
        private List<SegmentExportEntry> segments;

        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }

        public String getTemplateDescription() { return templateDescription; }
        public void setTemplateDescription(String templateDescription) { this.templateDescription = templateDescription; }

        public List<SegmentExportEntry> getSegments() { return segments; }
        public void setSegments(List<SegmentExportEntry> segments) { this.segments = segments; }

        public static class SegmentExportEntry {
            private String segmentName;
            private Integer position;
            private boolean enabled = true;
            private boolean pageBreakBefore = false;
            private Integer lockedVersion;
            private String conditionExpression;
            private Map<String, String> dataScope;
            private boolean component = false;

            public String getSegmentName() { return segmentName; }
            public void setSegmentName(String segmentName) { this.segmentName = segmentName; }
            public Integer getPosition() { return position; }
            public void setPosition(Integer position) { this.position = position; }
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public boolean isPageBreakBefore() { return pageBreakBefore; }
            public void setPageBreakBefore(boolean pageBreakBefore) { this.pageBreakBefore = pageBreakBefore; }
            public Integer getLockedVersion() { return lockedVersion; }
            public void setLockedVersion(Integer lockedVersion) { this.lockedVersion = lockedVersion; }
            public String getConditionExpression() { return conditionExpression; }
            public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
            public Map<String, String> getDataScope() { return dataScope; }
            public void setDataScope(Map<String, String> dataScope) { this.dataScope = dataScope; }
            public boolean isComponent() { return component; }
            public void setComponent(boolean component) { this.component = component; }
        }
    }
}
