package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchDeleteParameterRequest(
        @NotEmpty(message = "Parameter ID list must not be empty")
        List<@NotNull Long> ids
) {}
