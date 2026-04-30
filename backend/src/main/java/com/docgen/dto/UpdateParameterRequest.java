package com.docgen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * All fields are optional except version (required for optimistic locking).
 */
public record UpdateParameterRequest(
        @Size(max = 100, message = "Parameter name must not exceed 100 characters")
        String name,

        String parameterType,

        String dataType,

        Boolean required,

        String defaultValue,

        String description,

        Integer sortOrder,

        String expressionText,

        String expressionType,

        Map<String, Object> validationRules,

        @NotNull(message = "Version must not be null")
        Integer version
) {}
