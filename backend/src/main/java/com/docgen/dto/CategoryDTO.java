package com.docgen.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Nested categories via {@code children}.
 */
public class CategoryDTO {

    private Long id;
    private Long tenantId;
    private Long parentId;
    private String name;
    private int sortOrder;
    private Instant createdAt;
    private List<CategoryDTO> children = new ArrayList<>();

    public CategoryDTO() {}

    public CategoryDTO(Long id, Long tenantId, Long parentId, String name, int sortOrder, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.parentId = parentId;
        this.name = name;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<CategoryDTO> getChildren() { return children; }
    public void setChildren(List<CategoryDTO> children) { this.children = children; }
}
