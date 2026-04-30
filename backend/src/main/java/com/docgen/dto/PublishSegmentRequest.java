package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class PublishSegmentRequest {

    @NotBlank(message = "Segment name must not be blank")
    private String segmentName;

    private String comment;

    public PublishSegmentRequest() {}

    public String getSegmentName() { return segmentName; }
    public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
