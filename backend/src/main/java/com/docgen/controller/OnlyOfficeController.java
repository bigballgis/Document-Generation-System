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
 *       and a JWT token for OnlyOffice Document Server.</li>
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
     * Generate a presigned MinIO URL and OnlyOffice JWT token for the template.
     */
    @GetMapping("/{id}/onlyoffice-url")
    public ResponseEntity<Map<String, String>> getOnlyOfficeDocumentUrl(@PathVariable Long id) {
        String url = onlyOfficeService.generatePresignedUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Sign an OnlyOffice editor config payload with the server-side JWT secret.
     * The frontend builds the full config and sends it here for signing.
     */
    @PostMapping("/onlyoffice/sign")
    public ResponseEntity<Map<String, String>> signOnlyOfficeConfig(
            @RequestBody Map<String, Object> config) {
        String token = onlyOfficeService.generateEditorToken(config);
        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Callback endpoint invoked by OnlyOffice Document Server on document status changes.
     *
     * <p>Returns {@code {"error": 0}} when the callback is accepted (including benign no-ops).
     * Returns {@code {"error": 1}} when a save was not performed due to missing/invalid proof,
     * disallowed download URLs, empty downloads, or other validation failures so Document Server
     * does not treat the save as successful.
     */
    @PostMapping("/{id}/onlyoffice-callback")
    public ResponseEntity<Map<String, Integer>> handleCallback(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        log.info("OnlyOffice callback for template {}: status={}", id, body.get("status"));
        int error = onlyOfficeService.handleCallback(id, body, authorization);
        return ResponseEntity.ok(Map.of("error", error));
    }
}
