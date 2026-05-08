package com.docgen.dto;

import java.util.List;

/**
 * Categorized diffs between two template or segment versions plus a change summary.
 */
public class VersionDiffResult {

    private Long templateId;
    private Integer versionA;
    private Integer versionB;
    private List<String> differences;
    private List<DiffEntry> textDiffs;
    private List<DiffEntry> variableDiffs;
    private List<DiffEntry> dataSourceDiffs;
    private List<DiffEntry> expressionDiffs;
    private ChangeSummary changeSummary;

    public VersionDiffResult() {}

    public VersionDiffResult(Integer versionA, Integer versionB, List<String> differences) {
        this.versionA = versionA;
        this.versionB = versionB;
        this.differences = differences;
    }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Integer getVersionA() { return versionA; }
    public void setVersionA(Integer versionA) { this.versionA = versionA; }

    public Integer getVersionB() { return versionB; }
    public void setVersionB(Integer versionB) { this.versionB = versionB; }

    public List<String> getDifferences() { return differences; }
    public void setDifferences(List<String> differences) { this.differences = differences; }

    public List<DiffEntry> getTextDiffs() { return textDiffs; }
    public void setTextDiffs(List<DiffEntry> textDiffs) { this.textDiffs = textDiffs; }

    public List<DiffEntry> getVariableDiffs() { return variableDiffs; }
    public void setVariableDiffs(List<DiffEntry> variableDiffs) { this.variableDiffs = variableDiffs; }

    public List<DiffEntry> getDataSourceDiffs() { return dataSourceDiffs; }
    public void setDataSourceDiffs(List<DiffEntry> dataSourceDiffs) { this.dataSourceDiffs = dataSourceDiffs; }

    public List<DiffEntry> getExpressionDiffs() { return expressionDiffs; }
    public void setExpressionDiffs(List<DiffEntry> expressionDiffs) { this.expressionDiffs = expressionDiffs; }

    public ChangeSummary getChangeSummary() { return changeSummary; }
    public void setChangeSummary(ChangeSummary changeSummary) { this.changeSummary = changeSummary; }

    /**
     * Represents a single diff entry between two versions.
     */
    public static class DiffEntry {

        public enum ChangeType {
            ADDED, REMOVED, MODIFIED
        }

        private ChangeType changeType;
        private String field;
        private String oldValue;
        private String newValue;

        public DiffEntry() {}

        public DiffEntry(ChangeType changeType, String field, String oldValue, String newValue) {
            this.changeType = changeType;
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public ChangeType getChangeType() { return changeType; }
        public void setChangeType(ChangeType changeType) { this.changeType = changeType; }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getOldValue() { return oldValue; }
        public void setOldValue(String oldValue) { this.oldValue = oldValue; }

        public String getNewValue() { return newValue; }
        public void setNewValue(String newValue) { this.newValue = newValue; }
    }

    /**
     * Summary of changes between two versions.
     */
    public static class ChangeSummary {

        private int totalChanges;
        private int additions;
        private int deletions;
        private int modifications;

        public ChangeSummary() {}

        public ChangeSummary(int totalChanges, int additions, int deletions, int modifications) {
            this.totalChanges = totalChanges;
            this.additions = additions;
            this.deletions = deletions;
            this.modifications = modifications;
        }

        public int getTotalChanges() { return totalChanges; }
        public void setTotalChanges(int totalChanges) { this.totalChanges = totalChanges; }

        public int getAdditions() { return additions; }
        public void setAdditions(int additions) { this.additions = additions; }

        public int getDeletions() { return deletions; }
        public void setDeletions(int deletions) { this.deletions = deletions; }

        public int getModifications() { return modifications; }
        public void setModifications(int modifications) { this.modifications = modifications; }
    }
}
