package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for validating an expression.
 */
public class ValidateExpressionRequest {

    @NotBlank(message = "表达式内容不能为空")
    private String expression;

    @NotBlank(message = "表达式类型不能为空")
    private String expressionType;

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getExpressionType() { return expressionType; }
    public void setExpressionType(String expressionType) { this.expressionType = expressionType; }
}
