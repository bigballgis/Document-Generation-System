package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class PublishSegmentRequest {

    @NotBlank(message = "片段名称不能为空")
    private String segmentName;

    private String comment;

    public PublishSegmentRequest() {}

    public String getSegmentName() { return segmentName; }
    public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
