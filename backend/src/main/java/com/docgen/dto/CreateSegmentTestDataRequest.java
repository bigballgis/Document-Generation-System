package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating segment test data.
 */
public class CreateSegmentTestDataRequest {

    @NotBlank(message = "Test data name is required")
    private String name;

    @NotNull(message = "Test data JSON is required")
    private String testDataJson;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTestDataJson() { return testDataJson; }
    public void setTestDataJson(String testDataJson) { this.testDataJson = testDataJson; }
}
