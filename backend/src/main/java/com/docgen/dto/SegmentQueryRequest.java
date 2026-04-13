package com.docgen.dto;

import java.util.List;

/**
 * Request DTO for querying segments with filtering and pagination.
 */
public class SegmentQueryRequest {

    private String name;
    private List<Long> tagIds;
    private Long categoryId;
    private String segmentType;
    private Boolean isComponent;
    private int page;
    private int size;

    public SegmentQueryRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String segmentType) { this.segmentType = segmentType; }

    public Boolean getIsComponent() { return isComponent; }
    public void setIsComponent(Boolean isComponent) { this.isComponent = isComponent; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
