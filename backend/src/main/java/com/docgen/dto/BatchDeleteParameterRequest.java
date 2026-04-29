package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchDeleteParameterRequest(
        @NotEmpty(message = "参数ID列表不能为空")
        List<@NotNull Long> ids
) {}
