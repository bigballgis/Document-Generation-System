package com.docgen.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO representing a reviewer assignment for a specific segment.
 */
public class SegmentReviewerAssignment {

    @NotNull(message = "段落ID不能为空")
    private Long segmentId;

    @NotNull(message = "审查人ID不能为空")
    private Long reviewerId;

    public SegmentReviewerAssignment() {}

    public SegmentReviewerAssignment(Long segmentId, Long reviewerId) {
        this.segmentId = segmentId;
        this.reviewerId = reviewerId;
    }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
}
