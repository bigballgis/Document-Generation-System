package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateCompositeTemplateRequest {

    @NotBlank(message = "Composite template name must not be blank")
    @Size(max = 200, message = "Composite template name must not exceed 200 characters")
    private String name;

    private String description;

    private String outputFormat;

    private Long teamId;

    private Long categoryId;

    public CreateCompositeTemplateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
