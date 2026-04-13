package com.docgen.controller;

import com.docgen.dto.TemplateDTO;
import com.docgen.dto.UserPrincipal;
import com.docgen.service.TemplateImportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for template import/export endpoints (requirement 30).
 *
 * Endpoints:
 * - POST   /api/templates/import         — import .docx as new template
 * - GET    /api/templates/{id}/export     — export template as .docx
 * - GET    /api/templates/{id}/export-config — export full config as JSON
 * - POST   /api/templates/import-config   — import JSON config to restore template
 */
@RestController
@RequestMapping("/api/templates")
public class ImportExportController {

    private final TemplateImportExportService importExportService;

    public ImportExportController(TemplateImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDTO> importDocx(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO result = importExportService.importFromDocx(file, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long id) {
        byte[] content = importExportService.exportToDocx(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template.docx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(content);
    }

    @GetMapping("/{id}/export-config")
    public ResponseEntity<byte[]> exportConfig(@PathVariable Long id) {
        byte[] content = importExportService.exportConfig(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template-config.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
    }

    @PostMapping(value = "/import-config", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDTO> importConfig(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO result = importExportService.importConfig(file, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
