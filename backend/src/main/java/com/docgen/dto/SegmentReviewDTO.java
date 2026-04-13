package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for segment review information.
 */
public class SegmentReviewDTO {

    private Long id;
    private Long templateReviewId;
    private Long segmentId;
    private Long reviewerId;
    private String status;
    private String comment;
    private Instant createdAt;
    private Instant completedAt;

    public SegmentReviewDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateReviewId() { return templateReviewId; }
    public void setTemplateReviewId(Long templateReviewId) { this.templateReviewId = templateReviewId; }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
