package com.docgen.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for updating an existing segment.
 */
public class UpdateSegmentRequest {

    @Size(max = 200, message = "段落名称最长 200 个字符")
    private String name;

    private String description;

    private String segmentType;

    private Long categoryId;

    private List<Long> tagIds;

    public UpdateSegmentRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
}
