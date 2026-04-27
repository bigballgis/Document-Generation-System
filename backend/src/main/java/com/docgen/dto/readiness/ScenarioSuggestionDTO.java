package com.docgen.dto.readiness;

/**
 * Deterministic suggestion for improving scenario coverage (no LLM).
 */
public class ScenarioSuggestionDTO {

    private String type;
    /** Stable code for client-side i18n (e.g. BRANCH_MISSING_FALSE). */
    private String code;
    private String title;
    private String reason;
    private MissingBusinessSituationDTO sourceMissingSituation;

    public ScenarioSuggestionDTO() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public MissingBusinessSituationDTO getSourceMissingSituation() {
        return sourceMissingSituation;
    }

    public void setSourceMissingSituation(MissingBusinessSituationDTO sourceMissingSituation) {
        this.sourceMissingSituation = sourceMissingSituation;
    }
}
