package com.docgen.dto;

/**
 * DTO for a segment recommendation result.
 * Suggests segments that may be relevant to a composite template
 * based on type tags and usage patterns.
 */
public class SegmentRecommendationDTO {

    private Long segmentId;
    private String segmentName;
    private String segmentType;
    private String reason;
    private double relevanceScore;

    public SegmentRecommendationDTO() {}

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public String getSegmentName() { return segmentName; }
    public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
}
