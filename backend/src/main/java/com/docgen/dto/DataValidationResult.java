package com.docgen.dto;

import java.util.List;

/**
 * Result of data validation containing a list of errors (empty if valid).
 * <p>
 * Validates: Requirements 13.7
 */
public class DataValidationResult {

    private boolean valid;
    private List<DataValidationError> errors;

    public DataValidationResult() {}

    public DataValidationResult(boolean valid, List<DataValidationError> errors) {
        this.valid = valid;
        this.errors = errors;
    }

    public static DataValidationResult success() {
        return new DataValidationResult(true, List.of());
    }

    public static DataValidationResult failure(List<DataValidationError> errors) {
        return new DataValidationResult(false, errors);
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public List<DataValidationError> getErrors() { return errors; }
    public void setErrors(List<DataValidationError> errors) { this.errors = errors; }
}
