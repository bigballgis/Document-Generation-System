package com.docgen.dto;

import java.util.List;

/**
 * Result of pipeline configuration validation.
 * <p>
 * Reports whether the pipeline is valid and, if not,
 * provides details such as circular dependency paths.
 */
public class PipelineValidationResult {

    private boolean valid;
    private List<String> errors;
    private List<String> cyclePath;

    private PipelineValidationResult(boolean valid, List<String> errors, List<String> cyclePath) {
        this.valid = valid;
        this.errors = errors;
        this.cyclePath = cyclePath;
    }

    public static PipelineValidationResult success() {
        return new PipelineValidationResult(true, List.of(), null);
    }

    public static PipelineValidationResult failure(List<String> errors) {
        return new PipelineValidationResult(false, errors, null);
    }

    public static PipelineValidationResult circularDependency(List<String> cyclePath) {
        return new PipelineValidationResult(false,
                List.of("Circular dependency detected: " + String.join(" → ", cyclePath)),
                cyclePath);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getCyclePath() {
        return cyclePath;
    }
}
