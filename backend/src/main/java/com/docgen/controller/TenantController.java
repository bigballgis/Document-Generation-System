package com.docgen.controller;

import com.docgen.dto.CreateTenantRequest;
import com.docgen.dto.TenantDTO;
import com.docgen.dto.TenantUsageDTO;
import com.docgen.dto.UpdateTenantRequest;
import com.docgen.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for tenant management endpoints.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantDTO> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @GetMapping
    public ResponseEntity<Page<TenantDTO>> listTenants(Pageable pageable) {
        return ResponseEntity.ok(tenantService.listTenants(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDTO> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantDTO> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(tenantService.updateTenant(id, request));
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<TenantDTO> enableTenant(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.enableTenant(id));
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<TenantDTO> disableTenant(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.disableTenant(id));
    }

    @GetMapping("/{id}/usage")
    public ResponseEntity<TenantUsageDTO> getTenantUsage(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantUsage(id));
    }
}
