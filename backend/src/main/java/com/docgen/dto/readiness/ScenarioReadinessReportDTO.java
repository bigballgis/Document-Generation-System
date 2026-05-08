package com.docgen.dto.readiness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated scenario readiness returned by {@code GET /api/templates/{templateId}/scenario-readiness}.
 */
public class ScenarioReadinessReportDTO {

    private Long templateId;
    private ScenarioReadinessSummaryDTO readiness;
    private List<ScenarioReadinessCaseDTO> scenarios = new ArrayList<>();
    private List<ScenarioSuggestionDTO> suggestions = new ArrayList<>();
    private Instant checkedAt;
    private List<String> warnings = new ArrayList<>();

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public ScenarioReadinessSummaryDTO getReadiness() {
        return readiness;
    }

    public void setReadiness(ScenarioReadinessSummaryDTO readiness) {
        this.readiness = readiness;
    }

    public List<ScenarioReadinessCaseDTO> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<ScenarioReadinessCaseDTO> scenarios) {
        this.scenarios = scenarios != null ? scenarios : new ArrayList<>();
    }

    public List<ScenarioSuggestionDTO> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ScenarioSuggestionDTO> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }
}
