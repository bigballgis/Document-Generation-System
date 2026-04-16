package com.docgen.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for parameter definition information.
 * Supports tree structure via the children list.
 */
public class ParameterDTO {

    private Long id;
    private Long templateId;
    private Long parentId;
    private String name;
    private String parameterType;
    private String dataType;
    private boolean required;
    private String defaultValue;
    private String description;
    private int sortOrder;
    private String expressionText;
    private String expressionType;
    private Map<String, Object> validationRules;
    private int version;
    private String parameterPath;
    private List<ParameterDTO> children = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public ParameterDTO() {}

    // ── Getters and Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getParameterType() { return parameterType; }
    public void setParameterType(String parameterType) { this.parameterType = parameterType; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getExpressionText() { return expressionText; }
    public void setExpressionText(String expressionText) { this.expressionText = expressionText; }

    public String getExpressionType() { return expressionType; }
    public void setExpressionType(String expressionType) { this.expressionType = expressionType; }

    public Map<String, Object> getValidationRules() { return validationRules; }
    public void setValidationRules(Map<String, Object> validationRules) { this.validationRules = validationRules; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getParameterPath() { return parameterPath; }
    public void setParameterPath(String parameterPath) { this.parameterPath = parameterPath; }

    public List<ParameterDTO> getChildren() { return children; }
    public void setChildren(List<ParameterDTO> children) { this.children = children; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
