package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for expression information.
 */
public class ExpressionDTO {

    private Long id;
    private Long templateId;
    private String name;
    private String expressionType;
    private String expressionText;
    private String description;
    private int executionOrder;
    private Instant createdAt;

    public ExpressionDTO() {}

    public ExpressionDTO(Long id, Long templateId, String name, String expressionType,
                         String expressionText, String description, int executionOrder,
                         Instant createdAt) {
        this.id = id;
        this.templateId = templateId;
        this.name = name;
        this.expressionType = expressionType;
        this.expressionText = expressionText;
        this.description = description;
        this.executionOrder = executionOrder;
        this.createdAt = createdAt;
    }

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
