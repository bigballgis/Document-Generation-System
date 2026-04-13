package com.docgen.controller;

import com.docgen.dto.GrantPermissionRequest;
import com.docgen.dto.PermissionDTO;
import com.docgen.dto.UserPrincipal;
import com.docgen.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for template permission management.
 */
@RestController
@RequestMapping("/api/templates/{templateId}/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ResponseEntity<List<PermissionDTO>> getPermissions(@PathVariable Long templateId) {
        return ResponseEntity.ok(permissionService.getTemplatePermissions(templateId));
    }

    @PostMapping
    public ResponseEntity<PermissionDTO> grantPermission(
            @PathVariable Long templateId,
            @Valid @RequestBody GrantPermissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PermissionDTO result = permissionService.grantPermission(
                templateId, request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{permissionId}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable Long templateId,
            @PathVariable Long permissionId) {
        permissionService.revokePermission(templateId, permissionId);
        return ResponseEntity.noContent().build();
    }
}
