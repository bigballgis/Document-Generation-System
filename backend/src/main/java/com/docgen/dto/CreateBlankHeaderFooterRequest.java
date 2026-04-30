package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateBlankHeaderFooterRequest {

    @NotBlank(message = "Type must not be blank")
    @Pattern(regexp = "header|footer", message = "Type must be header or footer")
    private String type;

    public CreateBlankHeaderFooterRequest() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
