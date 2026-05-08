package com.docgen.dto.readiness;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate readiness metrics for a template (business-facing names).
 */
public class ScenarioReadinessSummaryDTO {

    private double overallReadiness;
    private double conditionalClauseReadiness;
    private double repeatingDetailReadiness;
    private double requiredInformationReadiness;

    private int totalBranches;
    private int coveredBranches;
    private int totalLoopScenarios;
    private int coveredLoopScenarios;
    private int totalParameters;
    private int coveredParameters;

    private List<MissingBusinessSituationDTO> missingSituations = new ArrayList<>();

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

    public int getTotalBranches() {
        return totalBranches;
    }

    public void setTotalBranches(int totalBranches) {
        this.totalBranches = totalBranches;
    }

    public int getCoveredBranches() {
        return coveredBranches;
    }

    public void setCoveredBranches(int coveredBranches) {
        this.coveredBranches = coveredBranches;
    }

    public int getTotalLoopScenarios() {
        return totalLoopScenarios;
    }

    public void setTotalLoopScenarios(int totalLoopScenarios) {
        this.totalLoopScenarios = totalLoopScenarios;
    }

    public int getCoveredLoopScenarios() {
        return coveredLoopScenarios;
    }

    public void setCoveredLoopScenarios(int coveredLoopScenarios) {
        this.coveredLoopScenarios = coveredLoopScenarios;
    }

    public int getTotalParameters() {
        return totalParameters;
    }

    public void setTotalParameters(int totalParameters) {
        this.totalParameters = totalParameters;
    }

    public int getCoveredParameters() {
        return coveredParameters;
    }

    public void setCoveredParameters(int coveredParameters) {
        this.coveredParameters = coveredParameters;
    }

    public List<MissingBusinessSituationDTO> getMissingSituations() {
        return missingSituations;
    }

    public void setMissingSituations(List<MissingBusinessSituationDTO> missingSituations) {
        this.missingSituations = missingSituations != null ? missingSituations : new ArrayList<>();
    }
}
