package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.service.TemplateTestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for template test case CRUD, execution, and import/export.
 */
@RestController
public class TemplateTestController {

    private final TemplateTestService templateTestService;

    public TemplateTestController(TemplateTestService templateTestService) {
        this.templateTestService = templateTestService;
    }

    @PostMapping("/api/templates/{templateId}/test-cases")
    public ResponseEntity<TestCaseDTO> createTestCase(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateTestCaseRequest request) {
        TestCaseDTO dto = templateTestService.createTestCase(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/api/templates/{templateId}/test-cases")
    public ResponseEntity<Page<TestCaseDTO>> listTestCases(
            @PathVariable Long templateId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(templateTestService.listTestCases(templateId, q, pageable));
    }

    @PutMapping("/api/test-cases/{testCaseId}")
    public ResponseEntity<TestCaseDTO> updateTestCase(
            @PathVariable Long testCaseId,
            @Valid @RequestBody CreateTestCaseRequest request) {
        return ResponseEntity.ok(templateTestService.updateTestCase(testCaseId, request));
    }

    @DeleteMapping("/api/test-cases/{testCaseId}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long testCaseId) {
        templateTestService.deleteTestCase(testCaseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/test-cases/{testCaseId}/run")
    public ResponseEntity<TestResultDTO> runTestCase(@PathVariable Long testCaseId) {
        return ResponseEntity.ok(templateTestService.runTestCase(testCaseId));
    }

    /**
     * Paged trial run history for a test case (read-only). Ordered by {@code executedAt DESC, id DESC}.
     */
    @GetMapping("/api/test-cases/{testCaseId}/results")
    public ResponseEntity<Page<TestResultDTO>> listTestResults(
            @PathVariable Long testCaseId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(templateTestService.listTestResults(testCaseId, pageable));
    }

    @PostMapping("/api/templates/{templateId}/test-cases/run-all")
    public ResponseEntity<TestReportDTO> runAllTests(@PathVariable Long templateId) {
        return ResponseEntity.ok(templateTestService.runAllTests(templateId));
    }

    @GetMapping(value = "/api/templates/{templateId}/test-cases/export",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportTestCases(@PathVariable Long templateId) {
        return ResponseEntity.ok(templateTestService.exportTestCases(templateId));
    }

    @PostMapping(value = "/api/templates/{templateId}/test-cases/import",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TestCaseDTO>> importTestCases(
            @PathVariable Long templateId,
            @RequestBody String json) {
        List<TestCaseDTO> imported = templateTestService.importTestCases(templateId, json);
        return ResponseEntity.status(HttpStatus.CREATED).body(imported);
    }
}
