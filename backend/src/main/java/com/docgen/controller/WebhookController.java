package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for webhook configuration CRUD and log querying.
 * Provides endpoints scoped to templates for managing webhooks,
 * and a log endpoint scoped to individual webhook configs.
 */
@RestController
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/api/templates/{templateId}/webhooks")
    public ResponseEntity<WebhookConfigDTO> createWebhook(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateWebhookRequest request) {
        WebhookConfigDTO dto = webhookService.createWebhook(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/api/templates/{templateId}/webhooks")
    public ResponseEntity<List<WebhookConfigDTO>> listWebhooks(
            @PathVariable Long templateId) {
        return ResponseEntity.ok(webhookService.listWebhooks(templateId));
    }

    @PutMapping("/api/webhooks/{webhookId}")
    public ResponseEntity<WebhookConfigDTO> updateWebhook(
            @PathVariable Long webhookId,
            @Valid @RequestBody UpdateWebhookRequest request) {
        return ResponseEntity.ok(webhookService.updateWebhook(webhookId, request));
    }

    @DeleteMapping("/api/webhooks/{webhookId}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long webhookId) {
        webhookService.deleteWebhook(webhookId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/webhooks/{webhookId}/logs")
    public ResponseEntity<Page<WebhookLogDTO>> getWebhookLogs(
            @PathVariable Long webhookId,
            Pageable pageable) {
        return ResponseEntity.ok(webhookService.getWebhookLogs(webhookId, pageable));
    }
}
