package com.docgen.dto.readiness;

import java.util.ArrayList;
import java.util.List;

/**
 * Readiness metrics for one named business scenario (linked to a test case).
 */
public class ScenarioReadinessCaseDTO {

    private Long testCaseId;
    private String scenarioName;
    private double overallReadiness;
    private double conditionalClauseReadiness;
    private double repeatingDetailReadiness;
    private double requiredInformationReadiness;
    private List<MissingBusinessSituationDTO> missingSituations = new ArrayList<>();

    public Long getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(Long testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public double getOverallReadiness() {
        return overallReadiness;
    }

    public void setOverallReadiness(double overallReadiness) {
        this.overallReadiness = overallReadiness;
    }

    public double getConditionalClauseReadiness() {
        return conditionalClauseReadiness;
    }

    public void setConditionalClauseReadiness(double conditionalClauseReadiness) {
        this.conditionalClauseReadiness = conditionalClauseReadiness;
    }

    public double getRepeatingDetailReadiness() {
        return repeatingDetailReadiness;
    }

    public void setRepeatingDetailReadiness(double repeatingDetailReadiness) {
        this.repeatingDetailReadiness = repeatingDetailReadiness;
    }

    public double getRequiredInformationReadiness() {
        return requiredInformationReadiness;
    }

    public void setRequiredInformationReadiness(double requiredInformationReadiness) {
        this.requiredInformationReadiness = requiredInformationReadiness;
    }

    public List<MissingBusinessSituationDTO> getMissingSituations() {
        return missingSituations;
    }

    public void setMissingSituations(List<MissingBusinessSituationDTO> missingSituations) {
        this.missingSituations = missingSituations != null ? missingSituations : new ArrayList<>();
    }
}
