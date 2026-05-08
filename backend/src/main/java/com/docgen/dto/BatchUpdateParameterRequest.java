package com.docgen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record BatchUpdateParameterRequest(
        @NotEmpty(message = "Update list must not be empty")
        List<@Valid BatchUpdateItem> items
) {

    /**
     * Individual parameter update item within a batch update request.
     */
    public record BatchUpdateItem(
            @NotNull(message = "Parameter ID must not be null")
            Long id,

            @NotNull(message = "Version must not be null")
            Integer version,

            String name,

            String parameterType,

            String dataType,

            Boolean required,

            String defaultValue,

            String description,

            Integer sortOrder,

            String expressionText,

            String expressionType,

            Map<String, Object> validationRules
    ) {}
}
