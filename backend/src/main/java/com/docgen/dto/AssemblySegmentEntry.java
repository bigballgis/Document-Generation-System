package com.docgen.dto;

import java.util.Map;

/**
 * Represents a single segment entry within an Assembly_Config.
 * Defines the segment reference, position, rendering options, and data scope mapping.
 */
public class AssemblySegmentEntry {

    private Long segmentId;
    private Integer position;
    private boolean enabled = true;
    private boolean pageBreakBefore = false;
    private Integer lockedVersion;
    private String conditionExpression;
    private Map<String, String> dataScope;

    public AssemblySegmentEntry() {}

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isPageBreakBefore() { return pageBreakBefore; }
    public void setPageBreakBefore(boolean pageBreakBefore) { this.pageBreakBefore = pageBreakBefore; }

    public Integer getLockedVersion() { return lockedVersion; }
    public void setLockedVersion(Integer lockedVersion) { this.lockedVersion = lockedVersion; }

    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }

    public Map<String, String> getDataScope() { return dataScope; }
    public void setDataScope(Map<String, String> dataScope) { this.dataScope = dataScope; }
}
