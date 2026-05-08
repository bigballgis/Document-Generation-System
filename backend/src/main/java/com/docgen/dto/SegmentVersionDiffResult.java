package com.docgen.dto;

import java.util.ArrayList;
import java.util.List;

public class SegmentVersionDiffResult {

    private Long templateId;
    private String segmentName;
    private Integer versionA;
    private Integer versionB;
    private List<SegmentDiffEntry> diffs;
    private boolean filePathChanged;
    private String oldFilePath;
    private String newFilePath;
    private List<ContentDiffLine> contentDiffs = new ArrayList<>();
    private boolean contentChanged = false;
    private boolean truncated = false;

    public SegmentVersionDiffResult() {}

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getSegmentName() { return segmentName; }
    public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

    public Integer getVersionA() { return versionA; }
    public void setVersionA(Integer versionA) { this.versionA = versionA; }

    public Integer getVersionB() { return versionB; }
    public void setVersionB(Integer versionB) { this.versionB = versionB; }

    public List<SegmentDiffEntry> getDiffs() { return diffs; }
    public void setDiffs(List<SegmentDiffEntry> diffs) { this.diffs = diffs; }

    public boolean isFilePathChanged() { return filePathChanged; }
    public void setFilePathChanged(boolean filePathChanged) { this.filePathChanged = filePathChanged; }

    public String getOldFilePath() { return oldFilePath; }
    public void setOldFilePath(String oldFilePath) { this.oldFilePath = oldFilePath; }

    public String getNewFilePath() { return newFilePath; }
    public void setNewFilePath(String newFilePath) { this.newFilePath = newFilePath; }

    public List<ContentDiffLine> getContentDiffs() { return contentDiffs; }
    public void setContentDiffs(List<ContentDiffLine> contentDiffs) { this.contentDiffs = contentDiffs; }

    public boolean isContentChanged() { return contentChanged; }
    public void setContentChanged(boolean contentChanged) { this.contentChanged = contentChanged; }

    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    public static class SegmentDiffEntry {
        private String field;
        private String changeType; // ADDED, REMOVED, MODIFIED
        private String oldValue;
        private String newValue;

        public SegmentDiffEntry() {}

        public SegmentDiffEntry(String field, String changeType, String oldValue, String newValue) {
            this.field = field;
            this.changeType = changeType;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }

        public String getOldValue() { return oldValue; }
        public void setOldValue(String oldValue) { this.oldValue = oldValue; }

        public String getNewValue() { return newValue; }
        public void setNewValue(String newValue) { this.newValue = newValue; }
    }
}
