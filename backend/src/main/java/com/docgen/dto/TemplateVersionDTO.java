package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for template version information.
 */
public class TemplateVersionDTO {

    private Long id;
    private Long templateId;
    private Integer versionNumber;
    private String templateFilePath;
    private String configJson;
    private Long createdBy;
    private Instant createdAt;

    public TemplateVersionDTO() {}

    public TemplateVersionDTO(Long id, Long templateId, Integer versionNumber,
                              String templateFilePath, String configJson,
                              Long createdBy, Instant createdAt) {
        this.id = id;
        this.templateId = templateId;
        this.versionNumber = versionNumber;
        this.templateFilePath = templateFilePath;
        this.configJson = configJson;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getTemplateFilePath() { return templateFilePath; }
    public void setTemplateFilePath(String templateFilePath) { this.templateFilePath = templateFilePath; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
