package com.docgen.dto;

import com.docgen.entity.ComparisonType;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new template test case.
 */
public class CreateTestCaseRequest {

    @NotBlank(message = "Test case name is required")
    private String name;

    @NotNull(message = "Test data JSON is required")
    @JsonAlias({"testData"})
    private String testDataJson;

    @JsonAlias({"expectedResult"})
    private String expectedResultJson;

    @JsonDeserialize(using = ComparisonTypeDeserializer.class)
    @JsonAlias({"compareMode"})
    private ComparisonType comparisonType;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTestDataJson() { return testDataJson; }
    public void setTestDataJson(String testDataJson) { this.testDataJson = testDataJson; }

    public String getExpectedResultJson() { return expectedResultJson; }
    public void setExpectedResultJson(String expectedResultJson) { this.expectedResultJson = expectedResultJson; }

    public ComparisonType getComparisonType() { return comparisonType; }
    public void setComparisonType(ComparisonType comparisonType) { this.comparisonType = comparisonType; }
}
