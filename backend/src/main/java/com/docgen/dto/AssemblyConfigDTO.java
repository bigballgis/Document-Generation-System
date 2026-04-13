package com.docgen.dto;

import java.util.List;

/**
 * DTO representing the assembly configuration of a Composite_Template.
 * Contains the ordered list of segment entries that define the document structure.
 */
public class AssemblyConfigDTO {

    private List<AssemblySegmentEntry> segments;

    public AssemblyConfigDTO() {}

    public List<AssemblySegmentEntry> getSegments() { return segments; }
    public void setSegments(List<AssemblySegmentEntry> segments) { this.segments = segments; }
}
