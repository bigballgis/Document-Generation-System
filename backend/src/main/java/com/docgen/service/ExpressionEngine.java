package com.docgen.service;

import com.docgen.dto.ExpressionValidationResult;
import com.docgen.entity.ExpressionType;

import java.util.List;
import java.util.Map;

/**
 * Engine for evaluating expressions (JavaScript and Excel formulas)
 * via the Docxtemplater Node.js service.
 */
public interface ExpressionEngine {

    /**
     * Evaluate a single expression in the given context.
     *
     * @param expression the expression text
     * @param type       the expression language type
     * @param context    data context for variable resolution
     * @return the evaluation result
     */
    Object evaluate(String expression, ExpressionType type, Map<String, Object> context);

    /**
     * Evaluate all expressions and return a map of result variable names to values.
     *
     * @param expressions list of expression configs (name, expression, type)
     * @param context     data context for variable resolution
     * @return map of result variable name to computed value
     */
    Map<String, Object> evaluateAll(List<ExpressionConfig> expressions, Map<String, Object> context);

    /**
     * Validate expression syntax without executing it.
     *
     * @param expression the expression text
     * @param type       the expression language type
     * @return validation result with error details if invalid
     */
    ExpressionValidationResult validateExpression(String expression, ExpressionType type);

    /**
     * Configuration for a single expression to evaluate.
     */
    record ExpressionConfig(String name, String expression, ExpressionType type) {}
}
