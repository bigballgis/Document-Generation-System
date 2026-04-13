package com.docgen.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO representing the result of a template coverage check.
 * Contains forward coverage (template tags → data bindings) and
 * reverse coverage (data source fields → template usage).
 */
public class CoverageReport {

    private Long templateId;
    private String templateName;
    private double coveragePercentage;
    private int totalTags;
    private int boundTags;
    private int unboundTags;
    private List<String> unboundTagNames;
    private List<String> unusedDataSourceFields;
    private boolean belowThreshold;
    private double threshold;
    private Instant checkedAt;

    public CoverageReport() {}

    // ── Getters and Setters ──

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public double getCoveragePercentage() { return coveragePercentage; }
    public void setCoveragePercentage(double coveragePercentage) { this.coveragePercentage = coveragePercentage; }

    public int getTotalTags() { return totalTags; }
    public void setTotalTags(int totalTags) { this.totalTags = totalTags; }

    public int getBoundTags() { return boundTags; }
    public void setBoundTags(int boundTags) { this.boundTags = boundTags; }

    public int getUnboundTags() { return unboundTags; }
    public void setUnboundTags(int unboundTags) { this.unboundTags = unboundTags; }

    public List<String> getUnboundTagNames() { return unboundTagNames; }
    public void setUnboundTagNames(List<String> unboundTagNames) { this.unboundTagNames = unboundTagNames; }

    public List<String> getUnusedDataSourceFields() { return unusedDataSourceFields; }
    public void setUnusedDataSourceFields(List<String> unusedDataSourceFields) { this.unusedDataSourceFields = unusedDataSourceFields; }

    public boolean isBelowThreshold() { return belowThreshold; }
    public void setBelowThreshold(boolean belowThreshold) { this.belowThreshold = belowThreshold; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}
