package com.docgen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for batch updating parameters.
 */
public record BatchUpdateParameterRequest(
        @NotEmpty(message = "更新列表不能为空")
        List<@Valid BatchUpdateItem> items
) {

    /**
     * Individual parameter update item within a batch update request.
     */
    public record BatchUpdateItem(
            @NotNull(message = "参数ID不能为空")
            Long id,

            @NotNull(message = "版本号不能为空")
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
