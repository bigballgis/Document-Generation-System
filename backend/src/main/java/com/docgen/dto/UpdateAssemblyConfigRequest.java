package com.docgen.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class UpdateAssemblyConfigRequest {

    @NotNull(message = "Segment list must not be null")
    private List<AssemblySegmentEntry> segments;

    public UpdateAssemblyConfigRequest() {}

    public List<AssemblySegmentEntry> getSegments() { return segments; }
    public void setSegments(List<AssemblySegmentEntry> segments) { this.segments = segments; }
}
