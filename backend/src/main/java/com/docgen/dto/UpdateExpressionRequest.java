package com.docgen.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an expression.
 * All fields are optional; only non-null fields are applied.
 */
public class UpdateExpressionRequest {

    @Size(max = 100, message = "表达式名称不能超过100个字符")
    private String name;

    private String expressionType;
    private String expressionText;
    private String description;
    private Integer executionOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExpressionType() { return expressionType; }
    public void setExpressionType(String expressionType) { this.expressionType = expressionType; }

    public String getExpressionText() { return expressionText; }
    public void setExpressionText(String expressionText) { this.expressionText = expressionText; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(Integer executionOrder) { this.executionOrder = executionOrder; }
}
