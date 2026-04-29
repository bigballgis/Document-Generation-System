package com.docgen.controller;

import com.docgen.dto.CoverageReport;
import com.docgen.service.CoverageCheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CoverageController {

    private final CoverageCheckService coverageCheckService;

    public CoverageController(CoverageCheckService coverageCheckService) {
        this.coverageCheckService = coverageCheckService;
    }

    /**
     * Get coverage report for a template.
     * Optionally accepts a threshold query parameter (default 100%).
     */
    @GetMapping("/api/templates/{templateId}/coverage")
    public ResponseEntity<CoverageReport> getCoverage(
            @PathVariable Long templateId,
            @RequestParam(required = false, defaultValue = "100.0") double threshold) {
        CoverageReport report = coverageCheckService.checkCoverage(templateId, threshold);
        return ResponseEntity.ok(report);
    }

    /**
     * Export coverage report in JSON format.
     * PDF export would require additional rendering infrastructure;
     * JSON is the primary supported format.
     */
    @GetMapping("/api/templates/{templateId}/coverage/export")
    public ResponseEntity<CoverageReport> exportCoverage(
            @PathVariable Long templateId,
            @RequestParam(required = false, defaultValue = "json") String format,
            @RequestParam(required = false, defaultValue = "100.0") double threshold) {
        CoverageReport report = coverageCheckService.checkCoverage(templateId, threshold);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"coverage-report-" + templateId + ".json\"")
                .body(report);
    }
}
