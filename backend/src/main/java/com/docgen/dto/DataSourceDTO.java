package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for data source information.
 * The configJson field has sensitive values masked for display.
 */
public class DataSourceDTO {

    private Long id;
    private Long templateId;
    private String name;
    private String type;
    private String configJson;
    private boolean cacheEnabled;
    private Integer cacheTtl;
    private int priority;
    private Instant createdAt;
    private Instant updatedAt;

    public DataSourceDTO() {}

    public DataSourceDTO(Long id, Long templateId, String name, String type,
                         String configJson, boolean cacheEnabled, Integer cacheTtl,
                         int priority, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.templateId = templateId;
        this.name = name;
        this.type = type;
        this.configJson = configJson;
        this.cacheEnabled = cacheEnabled;
        this.cacheTtl = cacheTtl;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public Integer getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Integer cacheTtl) { this.cacheTtl = cacheTtl; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
