package com.docgen.controller;

import com.docgen.dto.ApiKeyDTO;
import com.docgen.dto.CreateApiKeyRequest;
import com.docgen.dto.UserPrincipal;
import com.docgen.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiKeyDTO> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ApiKeyDTO result = apiKeyService.createApiKey(
                principal.getTenantId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyDTO>> listApiKeys(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(apiKeyService.listApiKeys(principal.getTenantId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiKey(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        apiKeyService.deleteApiKey(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<ApiKeyDTO> enableApiKey(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(apiKeyService.enableApiKey(id, principal.getTenantId()));
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<ApiKeyDTO> disableApiKey(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(apiKeyService.disableApiKey(id, principal.getTenantId()));
    }
}
