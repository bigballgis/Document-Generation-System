package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public record JsonImportRequest(
        @NotBlank(message = "JSON data must not be blank")
        String jsonData,

        Long parentId
) {}
