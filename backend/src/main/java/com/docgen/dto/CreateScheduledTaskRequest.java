package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateScheduledTaskRequest {

    @NotBlank(message = "Cron expression is required")
    private String cronExpression;

    private String paramsJson;

    private Integer maxRetries;

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
}
