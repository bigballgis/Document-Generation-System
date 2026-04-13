package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for scheduled task configuration.
 */
public class ScheduledTaskDTO {

    private Long id;
    private Long templateId;
    private String cronExpression;
    private boolean enabled;
    private String paramsJson;
    private int maxRetries;
    private Instant lastExecutionAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public Instant getLastExecutionAt() { return lastExecutionAt; }
    public void setLastExecutionAt(Instant lastExecutionAt) { this.lastExecutionAt = lastExecutionAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
