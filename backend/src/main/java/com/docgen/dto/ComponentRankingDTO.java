package com.docgen.dto;

/**
 * DTO for component template reuse ranking on the dashboard.
 */
public class ComponentRankingDTO {

    private Long segmentId;
    private String segmentName;
    private int referenceCount;

    public ComponentRankingDTO() {}

    public ComponentRankingDTO(Long segmentId, String segmentName, int referenceCount) {
        this.segmentId = segmentId;
        this.segmentName = segmentName;
        this.referenceCount = referenceCount;
    }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public String getSegmentName() { return segmentName; }
    public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

    public int getReferenceCount() { return referenceCount; }
    public void setReferenceCount(int referenceCount) { this.referenceCount = referenceCount; }
}
