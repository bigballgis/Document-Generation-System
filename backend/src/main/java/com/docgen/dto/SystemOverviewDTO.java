package com.docgen.dto;

public class SystemOverviewDTO {

    private long totalTemplates;
    private long activeTemplates;
    private long totalApiCalls;
    private long totalDocuments;
    private long compositeTemplateCount;

    public SystemOverviewDTO() {}

    public SystemOverviewDTO(long totalTemplates, long activeTemplates, long totalApiCalls, long totalDocuments) {
        this.totalTemplates = totalTemplates;
        this.activeTemplates = activeTemplates;
        this.totalApiCalls = totalApiCalls;
        this.totalDocuments = totalDocuments;
    }

    public long getTotalTemplates() { return totalTemplates; }
    public void setTotalTemplates(long totalTemplates) { this.totalTemplates = totalTemplates; }

    public long getActiveTemplates() { return activeTemplates; }
    public void setActiveTemplates(long activeTemplates) { this.activeTemplates = activeTemplates; }

    public long getTotalApiCalls() { return totalApiCalls; }
    public void setTotalApiCalls(long totalApiCalls) { this.totalApiCalls = totalApiCalls; }

    public long getTotalDocuments() { return totalDocuments; }
    public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }

    public long getCompositeTemplateCount() { return compositeTemplateCount; }
    public void setCompositeTemplateCount(long compositeTemplateCount) { this.compositeTemplateCount = compositeTemplateCount; }
}
