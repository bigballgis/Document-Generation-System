package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for batch deleting parameters.
 */
public record BatchDeleteParameterRequest(
        @NotEmpty(message = "参数ID列表不能为空")
        List<@NotNull Long> ids
) {}
