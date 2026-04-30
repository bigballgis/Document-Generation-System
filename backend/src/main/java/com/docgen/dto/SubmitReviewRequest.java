package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SubmitReviewRequest {

    @NotEmpty(message = "reviewerIds must not be empty")
    private List<Long> reviewerIds;

    private int reviewLevel = 1;

    public SubmitReviewRequest() {}

    public SubmitReviewRequest(List<Long> reviewerIds, int reviewLevel) {
        this.reviewerIds = reviewerIds;
        this.reviewLevel = reviewLevel;
    }

    public List<Long> getReviewerIds() { return reviewerIds; }
    public void setReviewerIds(List<Long> reviewerIds) { this.reviewerIds = reviewerIds; }

    public int getReviewLevel() { return reviewLevel; }
    public void setReviewLevel(int reviewLevel) { this.reviewLevel = reviewLevel; }
}
