package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.service.AggregationResolver;
import com.docgen.service.ParameterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Parameter", description = "Parameter definitions")
public class ParameterController {

    private static final Logger log = LoggerFactory.getLogger(ParameterController.class);

    private final ParameterService parameterService;
    private final AggregationResolver aggregationResolver;

    public ParameterController(ParameterService parameterService, AggregationResolver aggregationResolver) {
        this.parameterService = parameterService;
        this.aggregationResolver = aggregationResolver;
    }

    @PostMapping("/templates/{templateId}/parameters")
    @Operation(summary = "Create parameter definition")
    public ResponseEntity<ParameterDTO> createParameter(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateParameterRequest request) {
        ParameterDTO created = parameterService.createParameter(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/templates/{templateId}/parameters")
    @Operation(summary = "List parameters (tree or flat)")
    public ResponseEntity<List<ParameterDTO>> getParameters(
            @PathVariable Long templateId,
            @RequestParam(value = "flat", required = false, defaultValue = "false") boolean flat) {
        List<ParameterDTO> parameters = flat
                ? parameterService.getParameterFlat(templateId)
                : parameterService.getParameterTree(templateId);
        return ResponseEntity.ok(parameters);
    }

    @PutMapping("/parameters/{id}")
    @Operation(summary = "Update parameter definition")
    public ResponseEntity<ParameterDTO> updateParameter(
            @PathVariable Long id,
            @Valid @RequestBody UpdateParameterRequest request) {
        ParameterDTO updated = parameterService.updateParameter(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/parameters/{id}")
    @Operation(summary = "Delete parameter definition")
    public ResponseEntity<Void> deleteParameter(@PathVariable Long id) {
        parameterService.deleteParameter(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/templates/{templateId}/parameters/scan")
    @Operation(summary = "Scan template placeholders vs parameter table")
    public ResponseEntity<ScanResultDTO> scanPlaceholders(@PathVariable Long templateId) {
        ScanResultDTO result = parameterService.scanPlaceholders(templateId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/templates/{templateId}/parameters/auto-create")
    @Operation(summary = "Auto-create parameters for unmatched placeholders")
    public ResponseEntity<List<ParameterDTO>> autoCreateParameters(@PathVariable Long templateId) {
        List<ParameterDTO> created = parameterService.autoCreateParameters(templateId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/templates/{templateId}/parameter-schema")
    @Operation(summary = "Get parameter JSON Schema")
    public ResponseEntity<ParameterSchemaDTO> getParameterSchema(@PathVariable Long templateId) {
        ParameterSchemaDTO schema = parameterService.getParameterSchema(templateId);
        return ResponseEntity.ok(schema);
    }

    @GetMapping("/templates/{templateId}/aggregation-schema")
    @Operation(summary = "Get aggregation property schema")
    public ResponseEntity<List<AggregationSchemaDTO>> getAggregationSchema(@PathVariable Long templateId) {
        List<AggregationSchemaDTO> schema = aggregationResolver.getAggregationSchema(templateId);
        return ResponseEntity.ok(schema);
    }

    @PostMapping("/templates/{templateId}/parameters/batch-delete")
    @Operation(summary = "Batch delete parameter definitions")
    public ResponseEntity<Void> batchDeleteParameters(
            @PathVariable Long templateId,
            @Valid @RequestBody BatchDeleteParameterRequest request) {
        parameterService.batchDelete(templateId, request.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/templates/{templateId}/parameters/batch-update")
    @Operation(summary = "Batch update parameter definitions")
    public ResponseEntity<List<ParameterDTO>> batchUpdateParameters(
            @PathVariable Long templateId,
            @Valid @RequestBody BatchUpdateParameterRequest request) {
        List<ParameterDTO> updated = parameterService.batchUpdate(templateId, request.items());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/templates/{templateId}/parameters/json-import")
    @Operation(summary = "Import parameter definitions from JSON")
    public ResponseEntity<List<ParameterDTO>> jsonImportParameters(
            @PathVariable Long templateId,
            @Valid @RequestBody JsonImportRequest request) {
        List<ParameterDTO> imported = parameterService.jsonImport(templateId, request.jsonData(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(imported);
    }
}
