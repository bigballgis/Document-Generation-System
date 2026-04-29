package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public record JsonImportRequest(
        @NotBlank(message = "JSON 数据不能为空")
        String jsonData,

        Long parentId
) {}
