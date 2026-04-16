package com.docgen.controller;

import com.docgen.dto.*;
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

/**
 * REST controller for parameter definition CRUD, scan, auto-create, and schema endpoints.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Parameter", description = "参数表管理")
public class ParameterController {

    private static final Logger log = LoggerFactory.getLogger(ParameterController.class);

    private final ParameterService parameterService;

    public ParameterController(ParameterService parameterService) {
        this.parameterService = parameterService;
    }

    @PostMapping("/templates/{templateId}/parameters")
    @Operation(summary = "创建参数定义")
    public ResponseEntity<ParameterDTO> createParameter(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateParameterRequest request) {
        ParameterDTO created = parameterService.createParameter(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/templates/{templateId}/parameters")
    @Operation(summary = "获取参数列表（树形或扁平）")
    public ResponseEntity<List<ParameterDTO>> getParameters(
            @PathVariable Long templateId,
            @RequestParam(value = "flat", required = false, defaultValue = "false") boolean flat) {
        List<ParameterDTO> parameters = flat
                ? parameterService.getParameterFlat(templateId)
                : parameterService.getParameterTree(templateId);
        return ResponseEntity.ok(parameters);
    }

    @PutMapping("/parameters/{id}")
    @Operation(summary = "更新参数定义")
    public ResponseEntity<ParameterDTO> updateParameter(
            @PathVariable Long id,
            @Valid @RequestBody UpdateParameterRequest request) {
        ParameterDTO updated = parameterService.updateParameter(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/parameters/{id}")
    @Operation(summary = "删除参数定义")
    public ResponseEntity<Void> deleteParameter(@PathVariable Long id) {
        parameterService.deleteParameter(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/templates/{templateId}/parameters/scan")
    @Operation(summary = "扫描模板占位符并与参数表对比")
    public ResponseEntity<ScanResultDTO> scanPlaceholders(@PathVariable Long templateId) {
        ScanResultDTO result = parameterService.scanPlaceholders(templateId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/templates/{templateId}/parameters/auto-create")
    @Operation(summary = "自动创建未匹配占位符的参数定义")
    public ResponseEntity<List<ParameterDTO>> autoCreateParameters(@PathVariable Long templateId) {
        List<ParameterDTO> created = parameterService.autoCreateParameters(templateId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/templates/{templateId}/parameter-schema")
    @Operation(summary = "获取参数 Schema")
    public ResponseEntity<ParameterSchemaDTO> getParameterSchema(@PathVariable Long templateId) {
        ParameterSchemaDTO schema = parameterService.getParameterSchema(templateId);
        return ResponseEntity.ok(schema);
    }

    @PostMapping("/templates/{templateId}/parameters/batch-delete")
    @Operation(summary = "批量删除参数定义")
    public ResponseEntity<Void> batchDeleteParameters(
            @PathVariable Long templateId,
            @Valid @RequestBody BatchDeleteParameterRequest request) {
        parameterService.batchDelete(templateId, request.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/templates/{templateId}/parameters/batch-update")
    @Operation(summary = "批量更新参数定义")
    public ResponseEntity<List<ParameterDTO>> batchUpdateParameters(
            @PathVariable Long templateId,
            @Valid @RequestBody BatchUpdateParameterRequest request) {
        List<ParameterDTO> updated = parameterService.batchUpdate(templateId, request.items());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/templates/{templateId}/parameters/json-import")
    @Operation(summary = "从 JSON 导入参数定义")
    public ResponseEntity<List<ParameterDTO>> jsonImportParameters(
            @PathVariable Long templateId,
            @Valid @RequestBody JsonImportRequest request) {
        List<ParameterDTO> imported = parameterService.jsonImport(templateId, request.jsonData(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(imported);
    }
}
