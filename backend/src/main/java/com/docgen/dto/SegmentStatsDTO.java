package com.docgen.dto;

/**
 * DTO for segment statistics on the dashboard.
 */
public class SegmentStatsDTO {

    private long totalSegments;
    private long componentSegments;
    private long regularSegments;
    private long compositeTemplates;
    private long singleTemplates;

    public SegmentStatsDTO() {}

    public long getTotalSegments() { return totalSegments; }
    public void setTotalSegments(long totalSegments) { this.totalSegments = totalSegments; }

    public long getComponentSegments() { return componentSegments; }
    public void setComponentSegments(long componentSegments) { this.componentSegments = componentSegments; }

    public long getRegularSegments() { return regularSegments; }
    public void setRegularSegments(long regularSegments) { this.regularSegments = regularSegments; }

    public long getCompositeTemplates() { return compositeTemplates; }
    public void setCompositeTemplates(long compositeTemplates) { this.compositeTemplates = compositeTemplates; }

    public long getSingleTemplates() { return singleTemplates; }
    public void setSingleTemplates(long singleTemplates) { this.singleTemplates = singleTemplates; }
}
