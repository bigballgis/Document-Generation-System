package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateTemplateRequest {

    @NotBlank(message = "Template name must not be blank")
    private String name;

    private String description;

    private String outputFormat;

    private String storageStrategy;

    private boolean async;

    private Long teamId;

    private Long categoryId;

    private boolean reviewRequired;

    public CreateTemplateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }

    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public boolean isReviewRequired() { return reviewRequired; }
    public void setReviewRequired(boolean reviewRequired) { this.reviewRequired = reviewRequired; }
}
