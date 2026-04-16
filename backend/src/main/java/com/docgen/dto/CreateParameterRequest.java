package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request DTO for creating a parameter definition.
 */
public record CreateParameterRequest(
        @NotBlank(message = "参数名称不能为空")
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

        Long parentId
) {}
