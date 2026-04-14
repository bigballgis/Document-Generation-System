package com.docgen.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code templates} table.
 */
@Entity
@Table(name = "templates")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "template_file_path", nullable = false, length = 500)
    private String templateFilePath;

    @Column(name = "output_format", nullable = false, length = 20)
    private String outputFormat = "WORD";

    @Column(name = "storage_strategy", nullable = false, length = 20)
    private String storageStrategy = "TEMP";

    @Column(name = "is_async", nullable = false)
    private boolean async = false;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired = false;

    @Column(name = "allow_history_versions", nullable = false)
    private boolean allowHistoryVersions = true;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "template_type", nullable = false, length = 20)
    private String templateType = "SINGLE";

    @Column(name = "assembly_config", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String assemblyConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters and Setters ──

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

    public boolean isAllowHistoryVersions() { return allowHistoryVersions; }
    public void setAllowHistoryVersions(boolean allowHistoryVersions) { this.allowHistoryVersions = allowHistoryVersions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getAssemblyConfig() { return assemblyConfig; }
    public void setAssemblyConfig(String assemblyConfig) { this.assemblyConfig = assemblyConfig; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
