package com.docgen.controller;

import com.docgen.dto.BindVariableRequest;
import com.docgen.dto.TemplateVariableDTO;
import com.docgen.service.TemplateVariableService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for template variable management.
 */
@RestController
public class TemplateVariableController {

    private final TemplateVariableService templateVariableService;

    public TemplateVariableController(TemplateVariableService templateVariableService) {
        this.templateVariableService = templateVariableService;
    }

    /**
     * Get all variables for a template. Triggers a scan if no variables exist yet.
     */
    @GetMapping("/api/templates/{templateId}/variables")
    public ResponseEntity<List<TemplateVariableDTO>> listVariables(@PathVariable Long templateId) {
        List<TemplateVariableDTO> variables = templateVariableService.listVariables(templateId);
        return ResponseEntity.ok(variables);
    }

    /**
     * Scan the template file and sync variables (add new, remove stale).
     */
    @PostMapping("/api/templates/{templateId}/variables/scan")
    public ResponseEntity<List<TemplateVariableDTO>> scanVariables(@PathVariable Long templateId) {
        List<TemplateVariableDTO> variables = templateVariableService.scanVariables(templateId);
        return ResponseEntity.ok(variables);
    }

    /**
     * Bind a variable to a data source field or expression result.
     */
    @PutMapping("/api/templates/{templateId}/variables/{varId}/bind")
    public ResponseEntity<TemplateVariableDTO> bindVariable(
            @PathVariable Long templateId,
            @PathVariable Long varId,
            @Valid @RequestBody BindVariableRequest request) {
        TemplateVariableDTO bound = templateVariableService.bindVariable(templateId, varId, request);
        return ResponseEntity.ok(bound);
    }
}
