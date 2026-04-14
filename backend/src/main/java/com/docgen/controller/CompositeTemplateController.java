package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.service.*;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * REST controller for Composite_Template management.
 * Provides endpoints for creating composite templates, managing assembly configs,
 * previewing (full and selective), coverage, import/export, and segment upload.
 */
@RestController
@RequestMapping("/api/composite-templates")
public class CompositeTemplateController {

    private static final Logger log = LoggerFactory.getLogger(CompositeTemplateController.class);

    private final CompositeTemplateService compositeTemplateService;
    private final CompositeCoverageService compositeCoverageService;
    private final CompositeImportExportService compositeImportExportService;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public CompositeTemplateController(CompositeTemplateService compositeTemplateService,
                                       CompositeCoverageService compositeCoverageService,
                                       CompositeImportExportService compositeImportExportService,
                                       MinioClient minioClient) {
        this.compositeTemplateService = compositeTemplateService;
        this.compositeCoverageService = compositeCoverageService;
        this.compositeImportExportService = compositeImportExportService;
        this.minioClient = minioClient;
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
}
