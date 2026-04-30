package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateBlankSegmentRequest {

    @NotBlank(message = "Segment name must not be blank")
    private String name;

    private String segmentType;

    public CreateBlankSegmentRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }
}
