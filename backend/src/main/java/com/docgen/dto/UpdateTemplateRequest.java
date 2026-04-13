package com.docgen.dto;

/**
 * Request DTO for updating an existing template.
 */
public class UpdateTemplateRequest {

    private String name;

    private String description;

    private String outputFormat;

    private String storageStrategy;

    private Boolean async;

    private Long teamId;

    private Long categoryId;

    private Boolean reviewRequired;

    public UpdateTemplateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }

    public Boolean getAsync() { return async; }
    public void setAsync(Boolean async) { this.async = async; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Boolean getReviewRequired() { return reviewRequired; }
    public void setReviewRequired(Boolean reviewRequired) { this.reviewRequired = reviewRequired; }
}
