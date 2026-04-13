package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for template variable information.
 */
public class TemplateVariableDTO {

    private Long id;
    private Long templateId;
    private String name;
    private String variableType;
    private String defaultValue;
    private String description;
    private String bindingSource;
    private String bindingField;
    private boolean bound;
    private Instant createdAt;

    public TemplateVariableDTO() {}

    public TemplateVariableDTO(Long id, Long templateId, String name, String variableType,
                               String defaultValue, String description, String bindingSource,
                               String bindingField, boolean bound, Instant createdAt) {
        this.id = id;
        this.templateId = templateId;
        this.name = name;
        this.variableType = variableType;
        this.defaultValue = defaultValue;
        this.description = description;
        this.bindingSource = bindingSource;
        this.bindingField = bindingField;
        this.bound = bound;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVariableType() { return variableType; }
    public void setVariableType(String variableType) { this.variableType = variableType; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBindingSource() { return bindingSource; }
    public void setBindingSource(String bindingSource) { this.bindingSource = bindingSource; }

    public String getBindingField() { return bindingField; }
    public void setBindingField(String bindingField) { this.bindingField = bindingField; }

    public boolean isBound() { return bound; }
    public void setBound(boolean bound) { this.bound = bound; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
