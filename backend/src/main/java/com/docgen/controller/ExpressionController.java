package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.entity.ExpressionType;
import com.docgen.service.ExpressionCrudService;
import com.docgen.service.ExpressionEngine;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for expression CRUD and validation.
 */
@RestController
public class ExpressionController {

    private final ExpressionCrudService expressionCrudService;
    private final ExpressionEngine expressionEngine;

    public ExpressionController(ExpressionCrudService expressionCrudService,
                                ExpressionEngine expressionEngine) {
        this.expressionCrudService = expressionCrudService;
        this.expressionEngine = expressionEngine;
    }

    @PostMapping("/api/templates/{templateId}/expressions")
    public ResponseEntity<ExpressionDTO> createExpression(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateExpressionRequest request) {
        ExpressionDTO created = expressionCrudService.createExpression(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/api/templates/{templateId}/expressions")
    public ResponseEntity<List<ExpressionDTO>> listExpressions(@PathVariable Long templateId) {
        return ResponseEntity.ok(expressionCrudService.listExpressions(templateId));
    }

    @PutMapping("/api/expressions/{id}")
    public ResponseEntity<ExpressionDTO> updateExpression(
            @PathVariable Long id,
            @Valid @RequestBody UpdateExpressionRequest request) {
        return ResponseEntity.ok(expressionCrudService.updateExpression(id, request));
    }

    @DeleteMapping("/api/expressions/{id}")
    public ResponseEntity<Void> deleteExpression(@PathVariable Long id) {
        expressionCrudService.deleteExpression(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/expressions/validate")
    public ResponseEntity<ExpressionValidationResult> validateExpression(
            @Valid @RequestBody ValidateExpressionRequest request) {
        ExpressionType type = ExpressionType.valueOf(request.getExpressionType());
        ExpressionValidationResult result = expressionEngine.validateExpression(
                request.getExpression(), type);
        return ResponseEntity.ok(result);
    }
}
