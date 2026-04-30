package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateCategoryRequest {

    @NotBlank(message = "Category name must not be blank")
    private String name;

    private Long parentId;

    private Integer sortOrder;

    public CreateCategoryRequest() {}

    public CreateCategoryRequest(String name, Long parentId, Integer sortOrder) {
        this.name = name;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
