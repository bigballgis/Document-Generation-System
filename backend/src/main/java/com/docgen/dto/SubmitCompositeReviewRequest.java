package com.docgen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for submitting a composite template review with per-segment reviewer assignments.
 */
public class SubmitCompositeReviewRequest {

    @NotEmpty(message = "段落审查人分配列表不能为空")
    @Valid
    private List<SegmentReviewerAssignment> assignments;

    private int reviewLevel = 1;

    public SubmitCompositeReviewRequest() {}

    public List<SegmentReviewerAssignment> getAssignments() { return assignments; }
    public void setAssignments(List<SegmentReviewerAssignment> assignments) { this.assignments = assignments; }

    public int getReviewLevel() { return reviewLevel; }
    public void setReviewLevel(int reviewLevel) { this.reviewLevel = reviewLevel; }
}
