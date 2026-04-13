package com.docgen.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code template_variables} table.
 */
@Entity
@Table(name = "template_variables")
public class TemplateVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "variable_type", nullable = false, length = 20)
    private String variableType = "STRING";

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "binding_source", length = 20)
    private String bindingSource;

    @Column(name = "binding_field", length = 200)
    private String bindingField;

    @Column(name = "is_bound", nullable = false)
    private boolean bound = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters and Setters ──

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
