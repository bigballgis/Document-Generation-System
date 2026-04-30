package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class ValidateExpressionRequest {

    @NotBlank(message = "Expression text must not be blank")
    private String expression;

    @NotBlank(message = "Expression type must not be blank")
    private String expressionType;

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getExpressionType() { return expressionType; }
    public void setExpressionType(String expressionType) { this.expressionType = expressionType; }
}
