package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for importing parameters from JSON data.
 */
public record JsonImportRequest(
        @NotBlank(message = "JSON 数据不能为空")
        String jsonData,

        Long parentId
) {}
