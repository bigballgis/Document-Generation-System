package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a blank segment .docx file.
 */
public class CreateBlankSegmentRequest {

    @NotBlank(message = "片段名称不能为空")
    private String name;

    private String segmentType;

    public CreateBlankSegmentRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }
}
