package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a blank header or footer .docx file.
 */
public class CreateBlankHeaderFooterRequest {

    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "header|footer", message = "类型必须为 header 或 footer")
    private String type;

    public CreateBlankHeaderFooterRequest() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
