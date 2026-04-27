package com.docgen.controller;

import com.docgen.dto.readiness.ScenarioReadinessReportDTO;
import com.docgen.service.ScenarioReadinessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only scenario readiness for the template validation workspace.
 */
@RestController
public class ScenarioReadinessController {

    private final ScenarioReadinessService scenarioReadinessService;

    public ScenarioReadinessController(ScenarioReadinessService scenarioReadinessService) {
        this.scenarioReadinessService = scenarioReadinessService;
    }

    @GetMapping("/api/templates/{templateId}/scenario-readiness")
    public ResponseEntity<ScenarioReadinessReportDTO> getScenarioReadiness(@PathVariable Long templateId) {
        return ResponseEntity.ok(scenarioReadinessService.getScenarioReadiness(templateId));
    }
}
