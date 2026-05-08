package com.docgen.dto;

import java.time.Instant;
import java.util.List;

/**
 * Three-dimensional coverage: Branch / Loop / Parameter.
 */
public class CoverageReport {

    private Long templateId;
    private String templateName;

    private double branchCoverage;
    private double loopCoverage;
    private double parameterCoverage;
    private double overallCoverage;

    private int totalBranches;
    private int coveredBranches;
    private int totalLoopScenarios;
    private int coveredLoopScenarios;
    private int totalParameters;
    private int coveredParameters;

    private List<UncoveredItem> uncoveredItems;

    private boolean belowThreshold;
    private double threshold;
    private Instant checkedAt;

    private List<String> warnings;

    public CoverageReport() {}


    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public double getBranchCoverage() { return branchCoverage; }
    public void setBranchCoverage(double branchCoverage) { this.branchCoverage = branchCoverage; }

    public double getLoopCoverage() { return loopCoverage; }
    public void setLoopCoverage(double loopCoverage) { this.loopCoverage = loopCoverage; }

    public double getParameterCoverage() { return parameterCoverage; }
    public void setParameterCoverage(double parameterCoverage) { this.parameterCoverage = parameterCoverage; }

    public double getOverallCoverage() { return overallCoverage; }
    public void setOverallCoverage(double overallCoverage) { this.overallCoverage = overallCoverage; }

    public int getTotalBranches() { return totalBranches; }
    public void setTotalBranches(int totalBranches) { this.totalBranches = totalBranches; }

    public int getCoveredBranches() { return coveredBranches; }
    public void setCoveredBranches(int coveredBranches) { this.coveredBranches = coveredBranches; }

    public int getTotalLoopScenarios() { return totalLoopScenarios; }
    public void setTotalLoopScenarios(int totalLoopScenarios) { this.totalLoopScenarios = totalLoopScenarios; }

    public int getCoveredLoopScenarios() { return coveredLoopScenarios; }
    public void setCoveredLoopScenarios(int coveredLoopScenarios) { this.coveredLoopScenarios = coveredLoopScenarios; }

    public int getTotalParameters() { return totalParameters; }
    public void setTotalParameters(int totalParameters) { this.totalParameters = totalParameters; }

    public int getCoveredParameters() { return coveredParameters; }
    public void setCoveredParameters(int coveredParameters) { this.coveredParameters = coveredParameters; }

    public List<UncoveredItem> getUncoveredItems() { return uncoveredItems; }
    public void setUncoveredItems(List<UncoveredItem> uncoveredItems) { this.uncoveredItems = uncoveredItems; }

    public boolean isBelowThreshold() { return belowThreshold; }
    public void setBelowThreshold(boolean belowThreshold) { this.belowThreshold = belowThreshold; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}

