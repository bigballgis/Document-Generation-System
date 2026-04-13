package com.docgen.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code segment_reviews} table.
 * Represents an independent review assignment for a single segment
 * within a composite template review workflow.
 */
@Entity
@Table(name = "segment_reviews")
public class SegmentReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_review_id", nullable = false)
    private Long templateReviewId;

    @Column(name = "segment_id", nullable = false)
    private Long segmentId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters and Setters ──

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
