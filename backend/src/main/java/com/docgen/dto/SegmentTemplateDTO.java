package com.docgen.dto;

/**
 * DTO for a segment template (preset or custom).
 */
public class SegmentTemplateDTO {

    private Long id;
    private String name;
    private String description;
    private String segmentType;
    private boolean preset;
    private Long createdBy;

    public SegmentTemplateDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }

    public boolean isPreset() { return preset; }
    public void setPreset(boolean preset) { this.preset = preset; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
