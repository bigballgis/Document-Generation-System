package com.docgen.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code expressions} table.
 */
@Entity
@Table(name = "expressions")
public class Expression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "expression_type", nullable = false, length = 20)
    private String expressionType;

    @Column(name = "expression_text", nullable = false, columnDefinition = "TEXT")
    private String expressionText;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "execution_order", nullable = false)
    private int executionOrder = 0;

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

    public String getExpressionType() { return expressionType; }
    public void setExpressionType(String expressionType) { this.expressionType = expressionType; }

    public String getExpressionText() { return expressionText; }
    public void setExpressionText(String expressionText) { this.expressionText = expressionText; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
