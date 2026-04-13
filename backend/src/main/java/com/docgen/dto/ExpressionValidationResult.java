package com.docgen.dto;

/**
 * Result of expression syntax validation.
 */
public class ExpressionValidationResult {

    private boolean valid;
    private String errorMessage;
    private Integer errorPosition;

    public ExpressionValidationResult() {}

    public ExpressionValidationResult(boolean valid, String errorMessage, Integer errorPosition) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.errorPosition = errorPosition;
    }

    public static ExpressionValidationResult success() {
        return new ExpressionValidationResult(true, null, null);
    }

    public static ExpressionValidationResult failure(String errorMessage, Integer errorPosition) {
        return new ExpressionValidationResult(false, errorMessage, errorPosition);
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getErrorPosition() { return errorPosition; }
    public void setErrorPosition(Integer errorPosition) { this.errorPosition = errorPosition; }
}
