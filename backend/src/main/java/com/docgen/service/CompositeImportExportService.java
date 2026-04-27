package com.docgen.service;

import com.docgen.config.CompositeZipImportProperties;
import com.docgen.dto.*;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
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

    private final TemplateRepository templateRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final TestCaseRepository testCaseRepository;
    private final CompositeCoverageService compositeCoverageService;
    private final ParameterService parameterService;
    private final ParameterRepository parameterRepository;
    private final CompositeZipImportProperties zipImportProperties;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public CompositeImportExportService(TemplateRepository templateRepository,
                                        AssemblyConfigService assemblyConfigService,
                                        MinioClient minioClient,
                                        ObjectMapper objectMapper,
                                        TestCaseRepository testCaseRepository,
                                        CompositeCoverageService compositeCoverageService,
                                        ParameterService parameterService,
                                        ParameterRepository parameterRepository,
                                        CompositeZipImportProperties zipImportProperties) {
        this.templateRepository = templateRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
        this.testCaseRepository = testCaseRepository;
        this.compositeCoverageService = compositeCoverageService;
        this.parameterService = parameterService;
        this.parameterRepository = parameterRepository;
        this.zipImportProperties = zipImportProperties;
    }

    /**
     * Export a Composite_Template as a ZIP containing all Segment .docx files + config.json.
     * Downloads files directly from assembly_config filePath.
     */
    @Transactional(readOnly = true)
    public byte[] exportAsZip(Long compositeTemplateId) {
        Template template = findCompositeTemplateOrThrow(compositeTemplateId);

        if (!"ACTIVE".equals(template.getStatus()) && !"DRAFT".equals(template.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TEMPLATE_EXPORT_NOT_ACTIVE,
                    "Only ACTIVE or DRAFT templates can be exported as ZIP",
                    HttpStatus.BAD_REQUEST);
        }

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

                // Write header/footer .docx files
                Set<String> exportedHeaderFooters = new HashSet<>();
                for (AssemblySegmentEntry entry : config.getSegments()) {
                    exportHeaderFooterFile(zos, entry.getHeaderFilePath(), "headers/", exportedHeaderFooters);
                    exportHeaderFooterFile(zos, entry.getFooterFilePath(), "footers/", exportedHeaderFooters);
                }
            }

            // parameters.json
            try {
                List<ParameterDTO> paramTree = parameterService.getParameterTree(compositeTemplateId);
                List<ParameterExportEntry> paramExport = convertParameterTree(paramTree);
                byte[] paramBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(paramExport);
                zos.putNextEntry(new ZipEntry("parameters.json"));
                zos.write(paramBytes);
                zos.closeEntry();
            } catch (Exception e) {
                log.warn("Failed to export parameters, skipping: {}", e.getMessage());
            }

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
        Map<String, byte[]> headerFiles = new LinkedHashMap<>();
        Map<String, byte[]> footerFiles = new LinkedHashMap<>();
        CompositeExportConfig exportConfig = null;
        byte[] testDataBytes = null;
        byte[] parametersBytes = null;

        long maxArchiveBytes = zipImportProperties.getMaxArchiveBytes();
        if (maxArchiveBytes > 0) {
            long declaredSize = zipFile.getSize();
            if (declaredSize >= 0 && declaredSize > maxArchiveBytes) {
                throw zipImportRejected("ZIP file exceeds maximum allowed size");
            }
        }

        final InputStream rawIn;
        try {
            rawIn = zipFile.getInputStream();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "Failed to read uploaded ZIP file", HttpStatus.BAD_REQUEST, e);
        }
        InputStream sizedIn = (maxArchiveBytes > 0)
                ? new LimitedArchiveInputStream(rawIn, maxArchiveBytes)
                : rawIn;

        // Parse ZIP with entry/path/size/count limits (see composite-import.zip in application.yml)
        try (ZipInputStream zis = new ZipInputStream(sizedIn)) {
            ZipEntry entry;
            int fileEntryOrdinal = 0;
            long totalUncompressed = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                assertAllowedCompositeImportEntryPath(name);

                fileEntryOrdinal++;
                int maxEntries = zipImportProperties.getMaxEntryCount();
                if (maxEntries > 0 && fileEntryOrdinal > maxEntries) {
                    throw zipImportRejected("ZIP contains too many entries");
                }

                assertDeclaredZipEntrySizesWithinLimits(entry, zipImportProperties);

                long perEntryCap = zipImportProperties.getMaxEntryBytes();
                if ("config.json".equals(name)) {
                    byte[] content = readZipEntryBody(zis, perEntryCap);
                    totalUncompressed = addUncompressedTotalOrReject(totalUncompressed, content.length,
                            zipImportProperties.getMaxTotalUncompressedBytes());
                    exportConfig = objectMapper.readValue(content, CompositeExportConfig.class);
                } else if (name.startsWith("segments/") && name.endsWith(".docx")) {
                    String segmentName = name.substring("segments/".length(),
                            name.length() - ".docx".length());
                    byte[] content = readZipEntryBody(zis, perEntryCap);
                    totalUncompressed = addUncompressedTotalOrReject(totalUncompressed, content.length,
                            zipImportProperties.getMaxTotalUncompressedBytes());
                    segmentFiles.put(segmentName, content);
                } else if (name.startsWith("headers/") && name.endsWith(".docx")) {
                    String headerName = name.substring("headers/".length(),
                            name.length() - ".docx".length());
                    byte[] content = readZipEntryBody(zis, perEntryCap);
                    totalUncompressed = addUncompressedTotalOrReject(totalUncompressed, content.length,
                            zipImportProperties.getMaxTotalUncompressedBytes());
                    headerFiles.put(headerName, content);
                } else if (name.startsWith("footers/") && name.endsWith(".docx")) {
                    String footerName = name.substring("footers/".length(),
                            name.length() - ".docx".length());
                    byte[] content = readZipEntryBody(zis, perEntryCap);
                    totalUncompressed = addUncompressedTotalOrReject(totalUncompressed, content.length,
                            zipImportProperties.getMaxTotalUncompressedBytes());
                    footerFiles.put(footerName, content);
                } else if ("test-data.json".equals(name)) {
                    byte[] content = readZipEntryBody(zis, perEntryCap);
                    totalUncompressed = addUncompressedTotalOrReject(totalUncompressed, content.length,
                            zipImportProperties.getMaxTotalUncompressedBytes());
                    testDataBytes = content;
                } else if ("parameters.json".equals(name)) {
                    byte[] content = readZipEntryBody(zis, perEntryCap);
                    totalUncompressed = addUncompressedTotalOrReject(totalUncompressed, content.length,
                            zipImportProperties.getMaxTotalUncompressedBytes());
                    parametersBytes = content;
                } else if ("coverage-report.json".equals(name)) {
                    long drained = drainZipEntry(zis, perEntryCap);
                    totalUncompressed = addUncompressedTotalOrReject(totalUncompressed, drained,
                            zipImportProperties.getMaxTotalUncompressedBytes());
                } else {
                    throw zipImportRejected("ZIP contains an unsupported entry path");
                }
                zis.closeEntry();
            }
        } catch (BusinessException e) {
            throw e;
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

        // Upload header/footer files to MinIO
        Map<String, String> headerNameToPath = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> fileEntry : headerFiles.entrySet()) {
            String headerName = fileEntry.getKey();
            String filePath = uploadHeaderFooterToMinio(fileEntry.getValue(), headerName, tenantId, "headers");
            headerNameToPath.put(headerName, filePath);
        }
        Map<String, String> footerNameToPath = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> fileEntry : footerFiles.entrySet()) {
            String footerName = fileEntry.getKey();
            String filePath = uploadHeaderFooterToMinio(fileEntry.getValue(), footerName, tenantId, "footers");
            footerNameToPath.put(footerName, filePath);
        }

        // Build assembly config with inline filePath
        AssemblyConfigDTO assemblyConfig = rebuildAssemblyConfig(exportConfig, nameToFilePath,
                headerNameToPath, footerNameToPath);
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
        // Restore template properties from config
        if (exportConfig.getOutputFormat() != null) {
            template.setOutputFormat(exportConfig.getOutputFormat());
        }
        if (exportConfig.getStorageStrategy() != null) {
            template.setStorageStrategy(exportConfig.getStorageStrategy());
        }
        template.setAsync(exportConfig.isAsync());
        template.setReviewRequired(exportConfig.isReviewRequired());

        template = templateRepository.save(template);

        log.info("Imported composite template: name={}, id={}, segments={}",
                template.getName(), template.getId(), nameToFilePath.size());

        // Import parameters
        if (parametersBytes != null) {
            importParametersFromBytes(parametersBytes, template.getId());
        }

        if (testDataBytes != null) {
            importTestData(testDataBytes, template.getId());
        }

        return toTemplateDTO(template);
    }

    // ── Parameter tree conversion helpers ──

    private List<ParameterExportEntry> convertParameterTree(List<ParameterDTO> params) {
        if (params == null || params.isEmpty()) return List.of();
        List<ParameterExportEntry> result = new ArrayList<>();
        for (ParameterDTO p : params) {
            ParameterExportEntry entry = new ParameterExportEntry();
            entry.setName(p.getName());
            entry.setParameterType(p.getParameterType());
            entry.setDataType(p.getDataType());
            entry.setRequired(p.isRequired());
            entry.setDefaultValue(p.getDefaultValue());
            entry.setDescription(p.getDescription());
            entry.setSortOrder(p.getSortOrder());
            entry.setExpressionText(p.getExpressionText());
            entry.setExpressionType(p.getExpressionType());
            entry.setValidationRules(p.getValidationRules());
            entry.setChildren(convertParameterTree(p.getChildren()));
            result.add(entry);
        }
        return result;
    }

    private void importParameterTree(Long templateId, List<ParameterExportEntry> entries, Long parentId) {
        if (entries == null || entries.isEmpty()) return;
        for (ParameterExportEntry entry : entries) {
            ParameterDefinition entity = new ParameterDefinition();
            entity.setTemplateId(templateId);
            entity.setParentId(parentId);
            entity.setName(entry.getName());
            entity.setParameterType(entry.getParameterType() != null ? entry.getParameterType() : "REQUEST");
            entity.setDataType(entry.getDataType() != null ? entry.getDataType() : "STRING");
            entity.setRequired(entry.isRequired());
            entity.setDefaultValue(entry.getDefaultValue());
            entity.setDescription(entry.getDescription());
            entity.setSortOrder(entry.getSortOrder());
            entity.setExpressionText(entry.getExpressionText());
            entity.setExpressionType(entry.getExpressionType());
            if (entry.getValidationRules() != null) {
                try {
                    entity.setValidationRules(objectMapper.writeValueAsString(entry.getValidationRules()));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize validationRules for parameter '{}', skipping", entry.getName());
                }
            }
            ParameterDefinition saved = parameterRepository.save(entity);
            importParameterTree(templateId, entry.getChildren(), saved.getId());
        }
    }

    // ── Header/Footer export helpers ──

    private void exportHeaderFooterFile(ZipOutputStream zos, String filePath, String zipDir,
                                         Set<String> exported) {
        if (filePath == null || filePath.isBlank()) return;
        String baseName = extractFileBaseName(filePath);
        if (baseName == null || exported.contains(zipDir + baseName)) return;
        try {
            byte[] bytes = downloadFromMinio(filePath);
            zos.putNextEntry(new ZipEntry(zipDir + baseName + ".docx"));
            zos.write(bytes);
            zos.closeEntry();
            exported.add(zipDir + baseName);
        } catch (Exception e) {
            log.warn("Failed to export header/footer file '{}', skipping: {}", filePath, e.getMessage());
        }
    }

    private String extractFileBaseName(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        int lastSlash = filePath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
        int underscoreIdx = fileName.indexOf('_');
        if (underscoreIdx > 0 && underscoreIdx < fileName.length() - 1) {
            fileName = fileName.substring(underscoreIdx + 1);
        }
        if (fileName.toLowerCase().endsWith(".docx")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        return fileName.isBlank() ? null : fileName;
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
        export.setOutputFormat(template.getOutputFormat());
        export.setStorageStrategy(template.getStorageStrategy());
        export.setAsync(template.isAsync());
        export.setReviewRequired(template.isReviewRequired());
        export.setSourceStatus(template.getStatus());

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
                exportEntry.setHeaderFileName(extractFileBaseName(entry.getHeaderFilePath()));
                exportEntry.setFooterFileName(extractFileBaseName(entry.getFooterFilePath()));
                exportEntry.setPageNumberFormat(entry.getPageNumberFormat());
                exportEntry.setPageNumberStart(entry.getPageNumberStart());
                entries.add(exportEntry);
            }
        }
        export.setSegments(entries);
        return export;
    }

    private AssemblyConfigDTO rebuildAssemblyConfig(CompositeExportConfig exportConfig,
                                                     Map<String, String> nameToFilePath,
                                                     Map<String, String> headerNameToPath,
                                                     Map<String, String> footerNameToPath) {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        List<AssemblySegmentEntry> entries = new ArrayList<>();

        if (exportConfig.getSegments() != null) {
            for (CompositeExportConfig.SegmentExportEntry exportEntry : exportConfig.getSegments()) {
                String filePath = resolveImportedSegmentFilePath(nameToFilePath, exportEntry.getSegmentName());
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
                entry.setPageNumberFormat(exportEntry.getPageNumberFormat());
                entry.setPageNumberStart(exportEntry.getPageNumberStart());

                // Resolve header/footer file paths
                if (exportEntry.getHeaderFileName() != null) {
                    String headerPath = headerNameToPath.get(exportEntry.getHeaderFileName());
                    entry.setHeaderFilePath(headerPath);
                }
                if (exportEntry.getFooterFileName() != null) {
                    String footerPath = footerNameToPath.get(exportEntry.getFooterFileName());
                    entry.setFooterFilePath(footerPath);
                }

                entries.add(entry);
            }
        }
        config.setSegments(entries);
        return config;
    }

    private String resolveImportedSegmentFilePath(Map<String, String> nameToFilePath, String exportSegmentName) {
        if (exportSegmentName == null || exportSegmentName.isBlank()) {
            return null;
        }
        String direct = nameToFilePath.get(exportSegmentName);
        if (direct != null) {
            return direct;
        }
        String normalized = exportSegmentName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff_\\-]", "_");
        return nameToFilePath.get(normalized);
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
        String safeName = sanitizeMinioObjectNameComponent(segmentName);
        String objectPath = "segments/" + tenantId + "/" + uuid + "_" + safeName + ".docx";
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());
            return objectPath;
        } catch (Exception e) {
            log.error("Failed to upload segment file to MinIO: tenantId={}, sanitizedNameComponent={}",
                    tenantId, safeName, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to upload segment file", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Derives a single path-safe component for MinIO object keys. Must not be used for user-visible
     * assembly segment names ({@link AssemblySegmentEntry#setName} stays tied to export config / ZIP logic names).
     */
    private static String sanitizeMinioObjectNameComponent(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unnamed";
        }
        String s = raw.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff_-]", "_");
        s = s.replace("..", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        if (s.isBlank()) {
            return "unnamed";
        }
        final int maxLen = 120;
        if (s.length() > maxLen) {
            s = s.substring(0, maxLen);
        }
        return s;
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

    private String uploadHeaderFooterToMinio(byte[] content, String name, Long tenantId, String subDir) {
        String uuid = UUID.randomUUID().toString();
        String safeName = sanitizeMinioObjectNameComponent(name);
        String objectPath = "segments/" + tenantId + "/" + subDir + "/" + uuid + "_" + safeName + ".docx";
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());
            return objectPath;
        } catch (Exception e) {
            log.error("Failed to upload header/footer file to MinIO: tenantId={}, subDir={}, sanitizedNameComponent={}",
                    tenantId, subDir, safeName, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to upload header/footer file", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void importParametersFromBytes(byte[] bytes, Long templateId) {
        try {
            List<ParameterExportEntry> entries = objectMapper.readValue(bytes,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ParameterExportEntry.class));
            importParameterTree(templateId, entries, null);
            log.info("Imported {} top-level parameters for template {}", entries.size(), templateId);
        } catch (Exception e) {
            log.warn("Failed to import parameters: {}", e.getMessage());
        }
    }

    private Map<String, Object> toTestCaseExportMap(TestCase tc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tc.getName());
        map.put("testDataJson", tc.getTestDataJson());
        map.put("expectedResultJson", tc.getExpectedResultJson());
        map.put("comparisonType", tc.getComparisonType() != null ? tc.getComparisonType().name() : null);
        return map;
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

    private BusinessException zipImportRejected(String message) {
        return new BusinessException(ErrorCode.IMPORT_INVALID_FILE, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Only known composite export entry paths are accepted; segment/header/footer names must be a single path segment
     * without traversal markers.
     */
    private void assertAllowedCompositeImportEntryPath(String name) {
        if ("config.json".equals(name)
                || "test-data.json".equals(name)
                || "parameters.json".equals(name)
                || "coverage-report.json".equals(name)) {
            return;
        }
        if (name.startsWith("segments/") && name.endsWith(".docx")) {
            String inner = name.substring("segments/".length(), name.length() - ".docx".length());
            if (isSafeZipPathSegment(inner)) {
                return;
            }
            throw zipImportRejected("ZIP contains an invalid segment entry path");
        }
        if (name.startsWith("headers/") && name.endsWith(".docx")) {
            String inner = name.substring("headers/".length(), name.length() - ".docx".length());
            if (isSafeZipPathSegment(inner)) {
                return;
            }
            throw zipImportRejected("ZIP contains an invalid header entry path");
        }
        if (name.startsWith("footers/") && name.endsWith(".docx")) {
            String inner = name.substring("footers/".length(), name.length() - ".docx".length());
            if (isSafeZipPathSegment(inner)) {
                return;
            }
            throw zipImportRejected("ZIP contains an invalid footer entry path");
        }
        throw zipImportRejected("ZIP contains an unsupported entry path");
    }

    private static boolean isSafeZipPathSegment(String inner) {
        if (inner == null || inner.isEmpty()) {
            return false;
        }
        if (inner.contains("/") || inner.contains("\\")) {
            return false;
        }
        if (inner.contains("..")) {
            return false;
        }
        return !".".equals(inner);
    }

    private void assertDeclaredZipEntrySizesWithinLimits(ZipEntry entry, CompositeZipImportProperties p) {
        long declared = entry.getSize();
        long maxEntry = p.getMaxEntryBytes();
        if (maxEntry > 0 && declared > 0 && declared > maxEntry) {
            throw zipImportRejected("ZIP entry exceeds maximum allowed uncompressed size");
        }
        int ratio = p.getMaxUncompressedToCompressedRatio();
        long compressed = entry.getCompressedSize();
        if (ratio > 0 && declared > 0 && compressed > 0) {
            if ((double) declared / (double) compressed > ratio) {
                throw zipImportRejected("ZIP entry exceeds maximum allowed compression expansion ratio");
            }
        }
    }

    private long addUncompressedTotalOrReject(long current, long delta, long maxTotal) {
        if (maxTotal <= 0) {
            return current + delta;
        }
        if (delta < 0) {
            throw zipImportRejected("ZIP entry size is invalid");
        }
        if (current > Long.MAX_VALUE - delta) {
            throw zipImportRejected("ZIP uncompressed total size exceeds maximum allowed");
        }
        long next = current + delta;
        if (next > maxTotal) {
            throw zipImportRejected("ZIP uncompressed total size exceeds maximum allowed");
        }
        return next;
    }

    private byte[] readZipEntryBody(ZipInputStream zis, long maxEntryBytes) throws IOException {
        if (maxEntryBytes <= 0) {
            return zis.readAllBytes();
        }
        return readZipEntryBounded(zis, maxEntryBytes);
    }

    private byte[] readZipEntryBounded(ZipInputStream zis, long maxEntryBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        while (true) {
            int n = zis.read(buf);
            if (n < 0) {
                break;
            }
            if (total + (long) n > maxEntryBytes) {
                throw new IOException("ZIP entry exceeds maximum allowed uncompressed size");
            }
            total += n;
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private long drainZipEntry(ZipInputStream zis, long maxEntryBytes) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        while (true) {
            int n = zis.read(buf);
            if (n < 0) {
                break;
            }
            if (total + (long) n > maxEntryBytes) {
                throw new IOException("ZIP entry exceeds maximum allowed uncompressed size");
            }
            total += n;
        }
        return total;
    }

    private static final class LimitedArchiveInputStream extends FilterInputStream {
        private final long maxBytes;
        private long read;

        private LimitedArchiveInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                incrementOrThrow(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                incrementOrThrow(n);
            }
            return n;
        }

        private void incrementOrThrow(int n) throws IOException {
            if (maxBytes <= 0) {
                return;
            }
            if (read > Long.MAX_VALUE - n) {
                throw new IOException("ZIP file exceeds maximum allowed compressed size");
            }
            read += n;
            if (read > maxBytes) {
                throw new IOException("ZIP file exceeds maximum allowed compressed size");
            }
        }
    }

    /**
     * Internal DTO for the config.json structure in the export ZIP.
     */
    public static class CompositeExportConfig {
        private String templateName;
        private String templateDescription;
        private String outputFormat;
        private String storageStrategy;
        private boolean async;
        private boolean reviewRequired;
        private String sourceStatus;
        private List<SegmentExportEntry> segments;

        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }

        public String getTemplateDescription() { return templateDescription; }
        public void setTemplateDescription(String templateDescription) { this.templateDescription = templateDescription; }

        public String getOutputFormat() { return outputFormat; }
        public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

        public String getStorageStrategy() { return storageStrategy; }
        public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }

        public boolean isAsync() { return async; }
        public void setAsync(boolean async) { this.async = async; }

        public boolean isReviewRequired() { return reviewRequired; }
        public void setReviewRequired(boolean reviewRequired) { this.reviewRequired = reviewRequired; }

        public String getSourceStatus() { return sourceStatus; }
        public void setSourceStatus(String sourceStatus) { this.sourceStatus = sourceStatus; }

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
            private String headerFileName;
            private String footerFileName;
            private String pageNumberFormat;
            private Integer pageNumberStart;

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
            public String getHeaderFileName() { return headerFileName; }
            public void setHeaderFileName(String headerFileName) { this.headerFileName = headerFileName; }
            public String getFooterFileName() { return footerFileName; }
            public void setFooterFileName(String footerFileName) { this.footerFileName = footerFileName; }
            public String getPageNumberFormat() { return pageNumberFormat; }
            public void setPageNumberFormat(String pageNumberFormat) { this.pageNumberFormat = pageNumberFormat; }
            public Integer getPageNumberStart() { return pageNumberStart; }
            public void setPageNumberStart(Integer pageNumberStart) { this.pageNumberStart = pageNumberStart; }
        }
    }

    /**
     * Parameter definition export entry for parameters.json in the ZIP.
     * Mirrors ParameterDefinition fields without runtime IDs.
     */
    public static class ParameterExportEntry {
        private String name;
        private String parameterType;
        private String dataType;
        private boolean required;
        private String defaultValue;
        private String description;
        private int sortOrder;
        private String expressionText;
        private String expressionType;
        private Map<String, Object> validationRules;
        private List<ParameterExportEntry> children;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getParameterType() { return parameterType; }
        public void setParameterType(String parameterType) { this.parameterType = parameterType; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
        public String getExpressionText() { return expressionText; }
        public void setExpressionText(String expressionText) { this.expressionText = expressionText; }
        public String getExpressionType() { return expressionType; }
        public void setExpressionType(String expressionType) { this.expressionType = expressionType; }
        public Map<String, Object> getValidationRules() { return validationRules; }
        public void setValidationRules(Map<String, Object> validationRules) { this.validationRules = validationRules; }
        public List<ParameterExportEntry> getChildren() { return children; }
        public void setChildren(List<ParameterExportEntry> children) { this.children = children; }
    }
}
