package com.docgen.controller;

import com.docgen.service.OnlyOfficeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for OnlyOffice Document Server integration.
 *
 * <p>Provides two endpoints:
 * <ul>
 *   <li>{@code GET /api/templates/{id}/onlyoffice-url} — generates a MinIO presigned URL
 *       that OnlyOffice Document Server can use to download the template file directly,
 *       bypassing JWT authentication.</li>
 *   <li>{@code POST /api/templates/{id}/onlyoffice-callback} — receives save/status
 *       callbacks from OnlyOffice Document Server (unauthenticated, whitelisted in
 *       SecurityConfig).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/templates")
public class OnlyOfficeController {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeController.class);

    private final OnlyOfficeService onlyOfficeService;

    public OnlyOfficeController(OnlyOfficeService onlyOfficeService) {
        this.onlyOfficeService = onlyOfficeService;
    }

    /**
     * Generate a presigned MinIO URL for OnlyOffice to download the template.
     */
    @GetMapping("/{id}/onlyoffice-url")
    public ResponseEntity<Map<String, String>> getOnlyOfficeDocumentUrl(@PathVariable Long id) {
        String url = onlyOfficeService.generatePresignedUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Callback endpoint invoked by OnlyOffice Document Server on document status changes.
     * Must return {@code {"error": 0}} to acknowledge receipt.
     */
    @PostMapping("/{id}/onlyoffice-callback")
    public ResponseEntity<Map<String, Integer>> handleCallback(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        log.info("OnlyOffice callback for template {}: status={}", id, body.get("status"));
        onlyOfficeService.handleCallback(id, body);
        return ResponseEntity.ok(Map.of("error", 0));
    }
}
