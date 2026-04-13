package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating an expression.
 */
public class CreateExpressionRequest {

    @NotBlank(message = "表达式名称不能为空")
    @Size(max = 100, message = "表达式名称不能超过100个字符")
    private String name;

    @NotBlank(message = "表达式类型不能为空")
    private String expressionType;

    @NotBlank(message = "表达式内容不能为空")
    private String expressionText;

    private String description;

    private int executionOrder = 0;

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
}
