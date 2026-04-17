package com.docgen.dto;

import java.util.Map;

/**
 * Represents a single segment entry within an Assembly_Config.
 * Contains inline segment metadata (filePath, name, segmentType),
 * position, rendering options, and data scope mapping.
 */
public class AssemblySegmentEntry {

    private String filePath;
    private String name;
    private String segmentType;
    private Integer position;
    private boolean enabled = true;
    private boolean pageBreakBefore = false;
    private String conditionExpression;
    private Map<String, String> dataScope;

    // 控制节点扩展字段
    private String headerFilePath;
    private String footerFilePath;
    private String pageNumberFormat;
    private Integer pageNumberStart;

    public AssemblySegmentEntry() {}

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isPageBreakBefore() { return pageBreakBefore; }
    public void setPageBreakBefore(boolean pageBreakBefore) { this.pageBreakBefore = pageBreakBefore; }

    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }

    public Map<String, String> getDataScope() { return dataScope; }
    public void setDataScope(Map<String, String> dataScope) { this.dataScope = dataScope; }

    public String getHeaderFilePath() { return headerFilePath; }
    public void setHeaderFilePath(String headerFilePath) { this.headerFilePath = headerFilePath; }

    public String getFooterFilePath() { return footerFilePath; }
    public void setFooterFilePath(String footerFilePath) { this.footerFilePath = footerFilePath; }

    public String getPageNumberFormat() { return pageNumberFormat; }
    public void setPageNumberFormat(String pageNumberFormat) { this.pageNumberFormat = pageNumberFormat; }

    public Integer getPageNumberStart() { return pageNumberStart; }
    public void setPageNumberStart(Integer pageNumberStart) { this.pageNumberStart = pageNumberStart; }
}
