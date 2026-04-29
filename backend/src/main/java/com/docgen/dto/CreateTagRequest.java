package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateTagRequest {

    @NotBlank(message = "标签名称不能为空")
    private String name;

    public CreateTagRequest() {}

    public CreateTagRequest(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
