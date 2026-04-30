package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateTagRequest {

    @NotBlank(message = "Tag name must not be blank")
    private String name;

    public CreateTagRequest() {}

    public CreateTagRequest(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
