package com.docgen.dto;

import java.util.List;

/**
 * Ordered segment entries that define the composite document structure.
 */
public class AssemblyConfigDTO {

    private List<AssemblySegmentEntry> segments;

    public AssemblyConfigDTO() {}

    public List<AssemblySegmentEntry> getSegments() { return segments; }
    public void setSegments(List<AssemblySegmentEntry> segments) { this.segments = segments; }
}
