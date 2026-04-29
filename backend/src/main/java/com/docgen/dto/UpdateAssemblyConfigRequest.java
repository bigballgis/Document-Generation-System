package com.docgen.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class UpdateAssemblyConfigRequest {

    @NotNull(message = "段落列表不能为空")
    private List<AssemblySegmentEntry> segments;

    public UpdateAssemblyConfigRequest() {}

    public List<AssemblySegmentEntry> getSegments() { return segments; }
    public void setSegments(List<AssemblySegmentEntry> segments) { this.segments = segments; }
}
