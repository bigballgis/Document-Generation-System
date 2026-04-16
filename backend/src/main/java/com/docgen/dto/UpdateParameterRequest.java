package com.docgen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request DTO for updating a parameter definition.
 * All fields are optional except version (required for optimistic locking).
 */
public record UpdateParameterRequest(
        @Size(max = 100, message = "参数名称不能超过100个字符")
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

        @NotNull(message = "版本号不能为空")
        Integer version
) {}
