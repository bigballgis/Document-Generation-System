package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateParameterRequest(
        @NotBlank(message = "Parameter name must not be blank")
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

        Long parentId
) {}
