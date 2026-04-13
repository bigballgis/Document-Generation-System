package com.docgen.dto;

/**
 * Describes a single validation error for a specific field and rule.
 * <p>
 * Validates: Requirements 13.7
 */
public class DataValidationError {

    private String fieldName;
    private ValidationRule.RuleType ruleType;
    private String message;

    public DataValidationError() {}

    public DataValidationError(String fieldName, ValidationRule.RuleType ruleType, String message) {
        this.fieldName = fieldName;
        this.ruleType = ruleType;
        this.message = message;
    }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public ValidationRule.RuleType getRuleType() { return ruleType; }
    public void setRuleType(ValidationRule.RuleType ruleType) { this.ruleType = ruleType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
