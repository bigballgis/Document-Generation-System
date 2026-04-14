package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for template information.
 */
public class TemplateDTO {

    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String templateFilePath;
    private String outputFormat;
    private String storageStrategy;
    private boolean async;
    private Long teamId;
    private Long createdBy;
    private Long categoryId;
    private boolean reviewRequired;
    private String status;
    private String templateType;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public TemplateDTO() {}

    public TemplateDTO(Long id, Long tenantId, String name, String description,
                       String templateFilePath, String outputFormat, String storageStrategy,
                       boolean async, Long teamId, Long createdBy, Long categoryId,
                       boolean reviewRequired, String status,
                       Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.templateFilePath = templateFilePath;
        this.outputFormat = outputFormat;
        this.storageStrategy = storageStrategy;
        this.async = async;
        this.teamId = teamId;
        this.createdBy = createdBy;
        this.categoryId = categoryId;
        this.reviewRequired = reviewRequired;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTemplateFilePath() { return templateFilePath; }
    public void setTemplateFilePath(String templateFilePath) { this.templateFilePath = templateFilePath; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }

    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public boolean isReviewRequired() { return reviewRequired; }
    public void setReviewRequired(boolean reviewRequired) { this.reviewRequired = reviewRequired; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
