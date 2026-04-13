package com.docgen.dto;

import java.util.Map;

/**
 * Defines a single validation rule to apply against a data field.
 * <p>
 * Validates: Requirements 13.1-13.6
 */
public class ValidationRule {

    /**
     * Supported validation rule types.
     */
    public enum RuleType {
        REQUIRED,
        TYPE,
        RANGE,
        LENGTH,
        REGEX
    }

    /**
     * Supported data types for TYPE validation.
     */
    public enum DataType {
        STRING,
        NUMBER,
        BOOLEAN,
        DATE
    }

    private String fieldName;
    private RuleType ruleType;
    private Map<String, Object> parameters;

    public ValidationRule() {}

    public ValidationRule(String fieldName, RuleType ruleType, Map<String, Object> parameters) {
        this.fieldName = fieldName;
        this.ruleType = ruleType;
        this.parameters = parameters;
    }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public RuleType getRuleType() { return ruleType; }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
