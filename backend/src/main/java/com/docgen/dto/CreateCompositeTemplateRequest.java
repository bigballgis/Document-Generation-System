package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new Composite_Template.
 */
public class CreateCompositeTemplateRequest {

    @NotBlank(message = "组合模板名称不能为空")
    @Size(max = 200, message = "组合模板名称最长 200 个字符")
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
