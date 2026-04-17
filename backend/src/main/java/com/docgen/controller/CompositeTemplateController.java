package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.service.*;
import com.docgen.util.BlankDocxGenerator;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for Composite_Template management.
 * Provides endpoints for creating composite templates, managing assembly configs,
 * previewing (full and selective), coverage, import/export, and segment upload.
 */
@RestController
@RequestMapping("/api/composite-templates")
public class CompositeTemplateController {

    private static final Logger log = LoggerFactory.getLogger(CompositeTemplateController.class);

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 60;

    private final CompositeTemplateService compositeTemplateService;
    private final CompositeCoverageService compositeCoverageService;
    private final CompositeImportExportService compositeImportExportService;
    private final ContentIsolationValidator contentIsolationValidator;
    private final MinioClient minioClient;
    private final RestTemplate restTemplate;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public CompositeTemplateController(CompositeTemplateService compositeTemplateService,
                                       CompositeCoverageService compositeCoverageService,
                                       CompositeImportExportService compositeImportExportService,
                                       ContentIsolationValidator contentIsolationValidator,
                                       MinioClient minioClient,
                                       RestTemplate restTemplate) {
        this.compositeTemplateService = compositeTemplateService;
        this.compositeCoverageService = compositeCoverageService;
        this.compositeImportExportService = compositeImportExportService;
        this.contentIsolationValidator = contentIsolationValidator;
        this.minioClient = minioClient;
        this.restTemplate = restTemplate;
    }

    // ── Create ──

    @PostMapping
    public ResponseEntity<TemplateDTO> createCompositeTemplate(
            @Valid @RequestBody CreateCompositeTemplateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO created = compositeTemplateService.createCompositeTemplate(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── Assembly Config ──

    @GetMapping("/{id}/assembly-config")
    public ResponseEntity<AssemblyConfigDTO> getAssemblyConfig(@PathVariable Long id) {
        return ResponseEntity.ok(compositeTemplateService.getAssemblyConfig(id));
    }

    @PutMapping("/{id}/assembly-config")
    public ResponseEntity<AssemblyConfigDTO> updateAssemblyConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssemblyConfigRequest request) {
        return ResponseEntity.ok(compositeTemplateService.updateAssemblyConfig(id, request));
    }

    // ── Preview ──

    @PostMapping("/{id}/preview")
    public ResponseEntity<CompositePreviewDTO> previewCompositeTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(compositeTemplateService.previewCompositeTemplate(id));
    }

    @PostMapping("/{id}/preview/selective")
    public ResponseEntity<CompositePreviewDTO> previewSelective(
            @PathVariable Long id,
            @Valid @RequestBody SelectivePreviewRequest request) {
        return ResponseEntity.ok(
                compositeTemplateService.previewSelectiveSegments(id, request.getPositions()));
    }

    // ── Coverage ──

    @GetMapping("/{id}/coverage")
    public ResponseEntity<CompositeCoverageReport> getCoverage(@PathVariable Long id) {
        return ResponseEntity.ok(compositeCoverageService.checkCoverage(id));
    }

    // ── Import / Export ──

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportAsZip(@PathVariable Long id) {
        byte[] zipBytes = compositeImportExportService.exportAsZip(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"composite-template.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    @GetMapping("/{id}/export-config")
    public ResponseEntity<byte[]> exportConfig(@PathVariable Long id) {
        byte[] configBytes = compositeImportExportService.exportConfig(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"composite-config.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(configBytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDTO> importFromZip(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO result = compositeImportExportService.importFromZip(file, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ── Upload Segment ──

    /**
     * Upload a segment .docx file to MinIO and return an AssemblySegmentEntry with the filePath.
     */
    @PostMapping(value = "/{id}/upload-segment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssemblySegmentEntry> uploadSegment(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam(required = false) String segmentType) {
        // Verify template exists
        compositeTemplateService.getAssemblyConfig(id);

        String uuid = UUID.randomUUID().toString();
        String objectPath = "segments/" + id + "/" + uuid + "_" + name + ".docx";

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(file.getBytes()), file.getSize(), -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload segment file to MinIO: {}", objectPath, e);
            throw new com.docgen.exception.BusinessException(
                    com.docgen.exception.ErrorCode.INTERNAL_ERROR,
                    "Failed to upload segment file", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        AssemblySegmentEntry entry = new AssemblySegmentEntry();
        entry.setFilePath(objectPath);
        entry.setName(name);
        entry.setSegmentType(segmentType);
        entry.setEnabled(true);

        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    // ── Create Blank Segment ──

    /**
     * Create a blank .docx segment file, upload to MinIO, and return an AssemblySegmentEntry.
     */
    @PostMapping("/{id}/create-blank-segment")
    public ResponseEntity<AssemblySegmentEntry> createBlankSegment(
            @PathVariable Long id,
            @Valid @RequestBody CreateBlankSegmentRequest request) {
        // Verify template exists and is COMPOSITE type
        compositeTemplateService.getAssemblyConfig(id);

        String uuid = UUID.randomUUID().toString();
        String objectPath = "segments/" + id + "/" + uuid + "_" + request.getName() + ".docx";

        try {
            byte[] blankDocx = BlankDocxGenerator.generateBlankBody();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(blankDocx), blankDocx.length, -1)
                    .contentType(DOCX_CONTENT_TYPE)
                    .build());
        } catch (Exception e) {
            log.error("Failed to create blank segment for template {}: {}", id, objectPath, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "创建空白片段失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        AssemblySegmentEntry entry = new AssemblySegmentEntry();
        entry.setFilePath(objectPath);
        entry.setName(request.getName());
        entry.setSegmentType(request.getSegmentType());
        entry.setEnabled(true);

        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    // ── Create Blank Header/Footer ──

    /**
     * Create a blank header or footer .docx file, upload to MinIO, and return the filePath.
     */
    @PostMapping("/{id}/create-blank-header-footer")
    public ResponseEntity<Map<String, String>> createBlankHeaderFooter(
            @PathVariable Long id,
            @Valid @RequestBody CreateBlankHeaderFooterRequest request) {
        // Verify template exists and is COMPOSITE type
        compositeTemplateService.getAssemblyConfig(id);

        String type = request.getType();
        String uuid = UUID.randomUUID().toString();
        String objectPath;

        if ("header".equals(type)) {
            objectPath = "segments/" + id + "/headers/" + uuid + "_header.docx";
        } else {
            objectPath = "segments/" + id + "/footers/" + uuid + "_footer.docx";
        }

        try {
            byte[] blankDocx = "header".equals(type)
                    ? BlankDocxGenerator.generateBlankHeader()
                    : BlankDocxGenerator.generateBlankFooter();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(blankDocx), blankDocx.length, -1)
                    .contentType(DOCX_CONTENT_TYPE)
                    .build());
        } catch (Exception e) {
            log.error("Failed to create blank {} for template {}: {}", type, id, objectPath, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "创建空白" + ("header".equals(type) ? "页眉" : "页脚") + "失败",
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("filePath", objectPath));
    }

    // ── Segment OnlyOffice URL ──

    /**
     * Generate a MinIO presigned URL for a specific segment's .docx file.
     */
    @GetMapping("/{id}/segments/{segmentIndex}/onlyoffice-url")
    public ResponseEntity<Map<String, String>> getSegmentOnlyOfficeUrl(
            @PathVariable Long id,
            @PathVariable int segmentIndex) {
        AssemblyConfigDTO config = compositeTemplateService.getAssemblyConfig(id);
        List<AssemblySegmentEntry> segments = config.getSegments();

        if (segments == null || segmentIndex < 0 || segmentIndex >= segments.size()) {
            throw new BusinessException(ErrorCode.SEGMENT_FILE_NOT_FOUND,
                    "片段索引无效: " + segmentIndex, HttpStatus.NOT_FOUND);
        }

        String filePath = segments.get(segmentIndex).getFilePath();
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException(ErrorCode.SEGMENT_FILE_NOT_FOUND,
                    "片段文件路径为空", HttpStatus.NOT_FOUND);
        }

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(filePath)
                            .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for segment {}/{}: {}",
                    id, segmentIndex, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ONLYOFFICE_URL_FAILED,
                    "生成片段访问链接失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    // ── Segment OnlyOffice Callback ──

    /**
     * Handle OnlyOffice callback for a specific segment.
     * Downloads the edited .docx, validates content isolation, and saves to MinIO.
     */
    @PostMapping("/{id}/segments/{segmentIndex}/onlyoffice-callback")
    public ResponseEntity<Map<String, Integer>> handleSegmentCallback(
            @PathVariable Long id,
            @PathVariable int segmentIndex,
            @RequestParam(defaultValue = "body") String contentType,
            @RequestBody Map<String, Object> body) {
        log.info("OnlyOffice segment callback for template {}, segment {}: status={}",
                id, segmentIndex, body.get("status"));

        int status = body.get("status") instanceof Number
                ? ((Number) body.get("status")).intValue() : 0;

        // Only process status 2 (ready for saving) or 6 (forcesave)
        if (status != 2 && status != 6) {
            return ResponseEntity.ok(Map.of("error", 0));
        }

        String downloadUrl = (String) body.get("url");
        if (downloadUrl == null || downloadUrl.isBlank()) {
            log.warn("OnlyOffice segment callback has no download URL for template {}, segment {}",
                    id, segmentIndex);
            return ResponseEntity.ok(Map.of("error", 0));
        }

        AssemblyConfigDTO config = compositeTemplateService.getAssemblyConfig(id);
        List<AssemblySegmentEntry> segments = config.getSegments();

        if (segments == null || segmentIndex < 0 || segmentIndex >= segments.size()) {
            throw new BusinessException(ErrorCode.SEGMENT_FILE_NOT_FOUND,
                    "片段索引无效: " + segmentIndex, HttpStatus.NOT_FOUND);
        }

        String filePath = segments.get(segmentIndex).getFilePath();
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException(ErrorCode.SEGMENT_FILE_NOT_FOUND,
                    "片段文件路径为空", HttpStatus.NOT_FOUND);
        }

        try {
            // Download edited .docx from OnlyOffice
            byte[] editedContent = restTemplate.getForObject(downloadUrl, byte[].class);
            if (editedContent == null || editedContent.length == 0) {
                log.warn("Downloaded empty content from OnlyOffice for template {}, segment {}",
                        id, segmentIndex);
                return ResponseEntity.ok(Map.of("error", 0));
            }

            // Validate content isolation before saving
            contentIsolationValidator.validate(editedContent, contentType);

            // Save to MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .stream(new ByteArrayInputStream(editedContent), editedContent.length, -1)
                    .contentType(DOCX_CONTENT_TYPE)
                    .build());

            log.info("Saved edited segment from OnlyOffice for template {}, segment {}, size={} bytes",
                    id, segmentIndex, editedContent.length);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to save OnlyOffice edited segment for template {}, segment {}: {}",
                    id, segmentIndex, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                    "保存编辑片段失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return ResponseEntity.ok(Map.of("error", 0));
    }
}
