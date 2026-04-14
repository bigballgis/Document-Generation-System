package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.DataSource;
import com.docgen.entity.Expression;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Uses inline segment data from assembly_config (filePath-based, no SegmentRepository).
 */
@Service
public class CompositeImportExportService {

    private static final Logger log = LoggerFactory.getLogger(CompositeImportExportService.class);
    static final String CREDENTIAL_PLACEHOLDER = "__CREDENTIAL_PLACEHOLDER__";

    private final TemplateRepository templateRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final DataSourceRepository dataSourceRepository;
    private final ExpressionRepository expressionRepository;
    private final TestCaseRepository testCaseRepository;
    private final CompositeCoverageService compositeCoverageService;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public CompositeImportExportService(TemplateRepository templateRepository,
                                        AssemblyConfigService assemblyConfigService,
                                        MinioClient minioClient,
                                        ObjectMapper objectMapper,
                                        DataSourceRepository dataSourceRepository,
                                        ExpressionRepository expressionRepository,
                                        TestCaseRepository testCaseRepository,
                                        CompositeCoverageService compositeCoverageService) {
        this.templateRepository = templateRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
        this.dataSourceRepository = dataSourceRepository;
        this.expressionRepository = expressionRepository;
        this.testCaseRepository = testCaseRepository;
        this.compositeCoverageService = compositeCoverageService;
    }

    /**
     * Export a Composite_Template as a ZIP containing all Segment .docx files + config.json.
     * Downloads files directly from assembly_config filePath.
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

            // Write each segment .docx directly from inline filePath
            if (config.getSegments() != null) {
                Set<String> usedNames = new HashSet<>();
                for (AssemblySegmentEntry entry : config.getSegments()) {
                    if (entry.getFilePath() == null || entry.getFilePath().isBlank()) {
                        log.warn("Segment '{}' has no filePath during export, skipping", entry.getName());
                        continue;
                    }
                    byte[] docxBytes = downloadFromMinio(entry.getFilePath());
                    String fileName = uniqueFileName(entry.getName() != null ? entry.getName() : "segment", usedNames);
                    zos.putNextEntry(new ZipEntry("segments/" + fileName + ".docx"));
                    zos.write(docxBytes);
                    zos.closeEntry();
                }
            }

            // data-sources.json (credentials masked)
            List<DataSource> dataSources = dataSourceRepository.findByTemplateIdOrderByPriorityDesc(compositeTemplateId);
            List<Map<String, Object>> maskedDataSources = dataSources.stream()
                    .map(this::toMaskedDataSourceMap)
                    .toList();
            byte[] dsBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(maskedDataSources);
            zos.putNextEntry(new ZipEntry("data-sources.json"));
            zos.write(dsBytes);
            zos.closeEntry();

            // expressions.json
            List<Expression> expressions = expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(compositeTemplateId);
            byte[] exprBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                    expressions.stream().map(this::toExpressionExportMap).toList());
            zos.putNextEntry(new ZipEntry("expressions.json"));
            zos.write(exprBytes);
            zos.closeEntry();

            // test-data.json
            List<TestCase> testCases = testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(compositeTemplateId);
            byte[] testBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                    testCases.stream().map(this::toTestCaseExportMap).toList());
            zos.putNextEntry(new ZipEntry("test-data.json"));
            zos.write(testBytes);
            zos.closeEntry();

            // coverage-report.json (export-only)
            try {
                CompositeCoverageReport coverageReport = compositeCoverageService.checkCoverage(compositeTemplateId);
                byte[] covBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(coverageReport);
                zos.putNextEntry(new ZipEntry("coverage-report.json"));
                zos.write(covBytes);
                zos.closeEntry();
            } catch (Exception e) {
                log.warn("Failed to generate coverage report for export, skipping: {}", e.getMessage());
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
     * Uploads files to MinIO and writes filePath inline to assembly_config.
     */
    @Transactional
    public TemplateDTO importFromZip(MultipartFile zipFile, Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        Map<String, byte[]> segmentFiles = new LinkedHashMap<>();
        CompositeExportConfig exportConfig = null;
        byte[] dataSourcesBytes = null;
        byte[] expressionsBytes = null;
        byte[] testDataBytes = null;

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
                } else if ("data-sources.json".equals(name)) {
                    dataSourcesBytes = content;
                } else if ("expressions.json".equals(name)) {
                    expressionsBytes = content;
                } else if ("test-data.json".equals(name)) {
                    testDataBytes = content;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("Failed to parse import ZIP file", e);
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "Invalid ZIP file: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }

        if (exportConfig == null) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "ZIP must contain config.json", HttpStatus.BAD_REQUEST);
        }
        if (segmentFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "ZIP must contain at least one .docx file in segments/ directory",
                    HttpStatus.BAD_REQUEST);
        }

        // Upload segment files to MinIO and build name -> filePath mapping
        Map<String, String> nameToFilePath = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> fileEntry : segmentFiles.entrySet()) {
            String segmentName = fileEntry.getKey();
            byte[] docxBytes = fileEntry.getValue();
            String filePath = uploadToMinio(docxBytes, segmentName, tenantId);
            nameToFilePath.put(segmentName, filePath);
        }

        // Build assembly config with inline filePath
        AssemblyConfigDTO assemblyConfig = rebuildAssemblyConfig(exportConfig, nameToFilePath);
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
                template.getName(), template.getId(), nameToFilePath.size());

        if (dataSourcesBytes != null) {
            importDataSources(dataSourcesBytes, template.getId());
        }
        if (expressionsBytes != null) {
            importExpressions(expressionsBytes, template.getId());
        }
        if (testDataBytes != null) {
            importTestData(testDataBytes, template.getId());
        }

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
                exportEntry.setSegmentName(entry.getName() != null ? entry.getName() : "unknown");
                exportEntry.setFilePath(entry.getFilePath());
                exportEntry.setSegmentType(entry.getSegmentType());
                exportEntry.setPosition(entry.getPosition());
                exportEntry.setEnabled(entry.isEnabled());
                exportEntry.setPageBreakBefore(entry.isPageBreakBefore());
                exportEntry.setConditionExpression(entry.getConditionExpression());
                exportEntry.setDataScope(entry.getDataScope());
                entries.add(exportEntry);
            }
        }
        export.setSegments(entries);
        return export;
    }

    private AssemblyConfigDTO rebuildAssemblyConfig(CompositeExportConfig exportConfig,
                                                     Map<String, String> nameToFilePath) {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        List<AssemblySegmentEntry> entries = new ArrayList<>();

        if (exportConfig.getSegments() != null) {
            for (CompositeExportConfig.SegmentExportEntry exportEntry : exportConfig.getSegments()) {
                String filePath = nameToFilePath.get(exportEntry.getSegmentName());
                if (filePath == null) continue;

                AssemblySegmentEntry entry = new AssemblySegmentEntry();
                entry.setFilePath(filePath);
                entry.setName(exportEntry.getSegmentName());
                entry.setSegmentType(exportEntry.getSegmentType());
                entry.setPosition(exportEntry.getPosition());
                entry.setEnabled(exportEntry.isEnabled());
                entry.setPageBreakBefore(exportEntry.isPageBreakBefore());
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

    // ── Export helper methods ──

    Map<String, Object> toMaskedDataSourceMap(DataSource ds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", ds.getName());
        map.put("type", ds.getType());
        map.put("cacheEnabled", ds.isCacheEnabled());
        map.put("cacheTtl", ds.getCacheTtl());
        map.put("priority", ds.getPriority());

        try {
            Map<String, Object> config = objectMapper.readValue(ds.getConfigJson(),
                    new TypeReference<Map<String, Object>>() {});
            maskCredentialFields(config, ds.getType());
            map.put("config", config);
        } catch (Exception e) {
            log.warn("Failed to parse configJson for data source {}, exporting raw", ds.getId());
            map.put("config", ds.getConfigJson());
        }
        return map;
    }

    void maskCredentialFields(Map<String, Object> config, String type) {
        if ("DATABASE".equals(type)) {
            if (config.containsKey("password")) {
                config.put("password", CREDENTIAL_PLACEHOLDER);
            }
        }
        if (config.containsKey("apiKey")) {
            config.put("apiKey", CREDENTIAL_PLACEHOLDER);
        }
        if (config.containsKey("clientSecret")) {
            config.put("clientSecret", CREDENTIAL_PLACEHOLDER);
        }
        if (config.containsKey("auth") && config.get("auth") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> auth = (Map<String, Object>) config.get("auth");
            if (auth.containsKey("apiKey")) auth.put("apiKey", CREDENTIAL_PLACEHOLDER);
            if (auth.containsKey("clientSecret")) auth.put("clientSecret", CREDENTIAL_PLACEHOLDER);
            if (auth.containsKey("password")) auth.put("password", CREDENTIAL_PLACEHOLDER);
        }
    }

    private Map<String, Object> toExpressionExportMap(Expression expr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", expr.getName());
        map.put("expressionType", expr.getExpressionType());
        map.put("expressionText", expr.getExpressionText());
        map.put("description", expr.getDescription());
        map.put("executionOrder", expr.getExecutionOrder());
        return map;
    }

    private Map<String, Object> toTestCaseExportMap(TestCase tc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tc.getName());
        map.put("testDataJson", tc.getTestDataJson());
        map.put("expectedResultJson", tc.getExpectedResultJson());
        map.put("comparisonType", tc.getComparisonType() != null ? tc.getComparisonType().name() : null);
        return map;
    }

    // ── Import helper methods ──

    private void importDataSources(byte[] bytes, Long templateId) {
        try {
            List<Map<String, Object>> dsList = objectMapper.readValue(bytes,
                    new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> dsMap : dsList) {
                DataSource ds = new DataSource();
                ds.setTemplateId(templateId);
                ds.setName((String) dsMap.get("name"));
                ds.setType((String) dsMap.get("type"));
                ds.setCacheEnabled(Boolean.TRUE.equals(dsMap.get("cacheEnabled")));
                ds.setCacheTtl(dsMap.get("cacheTtl") != null ? ((Number) dsMap.get("cacheTtl")).intValue() : 300);
                ds.setPriority(dsMap.get("priority") != null ? ((Number) dsMap.get("priority")).intValue() : 0);
                Object config = dsMap.get("config");
                ds.setConfigJson(config instanceof String ? (String) config : objectMapper.writeValueAsString(config));
                dataSourceRepository.save(ds);
            }
            log.info("Imported {} data sources for template {}", dsList.size(), templateId);
        } catch (Exception e) {
            log.warn("Failed to import data sources: {}", e.getMessage());
        }
    }

    private void importExpressions(byte[] bytes, Long templateId) {
        try {
            List<Map<String, Object>> exprList = objectMapper.readValue(bytes,
                    new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> exprMap : exprList) {
                Expression expr = new Expression();
                expr.setTemplateId(templateId);
                expr.setName((String) exprMap.get("name"));
                expr.setExpressionType((String) exprMap.get("expressionType"));
                expr.setExpressionText((String) exprMap.get("expressionText"));
                expr.setDescription((String) exprMap.get("description"));
                expr.setExecutionOrder(exprMap.get("executionOrder") != null
                        ? ((Number) exprMap.get("executionOrder")).intValue() : 0);
                expressionRepository.save(expr);
            }
            log.info("Imported {} expressions for template {}", exprList.size(), templateId);
        } catch (Exception e) {
            log.warn("Failed to import expressions: {}", e.getMessage());
        }
    }

    private void importTestData(byte[] bytes, Long templateId) {
        try {
            List<Map<String, Object>> testList = objectMapper.readValue(bytes,
                    new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> tcMap : testList) {
                TestCase tc = new TestCase();
                tc.setTemplateId(templateId);
                tc.setName((String) tcMap.get("name"));
                tc.setTestDataJson((String) tcMap.get("testDataJson"));
                tc.setExpectedResultJson((String) tcMap.get("expectedResultJson"));
                String compType = (String) tcMap.get("comparisonType");
                if (compType != null) {
                    tc.setComparisonType(com.docgen.entity.ComparisonType.valueOf(compType));
                }
                testCaseRepository.save(tc);
            }
            log.info("Imported {} test cases for template {}", testList.size(), templateId);
        } catch (Exception e) {
            log.warn("Failed to import test data: {}", e.getMessage());
        }
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
            private String filePath;
            private String segmentType;
            private Integer position;
            private boolean enabled = true;
            private boolean pageBreakBefore = false;
            private String conditionExpression;
            private Map<String, String> dataScope;

            public String getSegmentName() { return segmentName; }
            public void setSegmentName(String segmentName) { this.segmentName = segmentName; }
            public String getFilePath() { return filePath; }
            public void setFilePath(String filePath) { this.filePath = filePath; }
            public String getSegmentType() { return segmentType; }
            public void setSegmentType(String segmentType) { this.segmentType = segmentType; }
            public Integer getPosition() { return position; }
            public void setPosition(Integer position) { this.position = position; }
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public boolean isPageBreakBefore() { return pageBreakBefore; }
            public void setPageBreakBefore(boolean pageBreakBefore) { this.pageBreakBefore = pageBreakBefore; }
            public String getConditionExpression() { return conditionExpression; }
            public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
            public Map<String, String> getDataScope() { return dataScope; }
            public void setDataScope(Map<String, String> dataScope) { this.dataScope = dataScope; }
        }
    }
}
