package com.docgen.dto;

import com.docgen.entity.ReviewStatus;

import java.time.Instant;
import java.util.List;

public class TemplateReviewDTO {

    private Long id;
    private Long templateId;
    private Long reviewerId;
    private int reviewLevel;
    private ReviewStatus status;
    private String comment;
    private List<String> suggestions;
    private Instant createdAt;
    private Instant completedAt;

    public TemplateReviewDTO() {}

    public TemplateReviewDTO(Long id, Long templateId, Long reviewerId, int reviewLevel,
                             ReviewStatus status, String comment, List<String> suggestions,
                             Instant createdAt, Instant completedAt) {
        this.id = id;
        this.templateId = templateId;
        this.reviewerId = reviewerId;
        this.reviewLevel = reviewLevel;
        this.status = status;
        this.comment = comment;
        this.suggestions = suggestions;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }

    public int getReviewLevel() { return reviewLevel; }
    public void setReviewLevel(int reviewLevel) { this.reviewLevel = reviewLevel; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
