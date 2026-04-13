package com.docgen.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for saving a segment as a custom template.
 */
public class CreateSegmentTemplateRequest {

    @NotNull(message = "段落 ID 不能为空")
    private Long segmentId;

    public CreateSegmentTemplateRequest() {}

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }
}
