package com.docgen.dto;

import com.docgen.entity.ComparisonType;

import java.time.Instant;

public class TestCaseDTO {

    private Long id;
    private Long templateId;
    private String name;
    private String testDataJson;
    private String expectedResultJson;
    private ComparisonType comparisonType;
    private Instant createdAt;
    private Instant updatedAt;

    /** Latest execution result for this case (list endpoint only; omitted in export payloads). */
    private TestResultDTO lastRun;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTestDataJson() { return testDataJson; }
    public void setTestDataJson(String testDataJson) { this.testDataJson = testDataJson; }

    public String getExpectedResultJson() { return expectedResultJson; }
    public void setExpectedResultJson(String expectedResultJson) { this.expectedResultJson = expectedResultJson; }

    public ComparisonType getComparisonType() { return comparisonType; }
    public void setComparisonType(ComparisonType comparisonType) { this.comparisonType = comparisonType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public TestResultDTO getLastRun() { return lastRun; }
    public void setLastRun(TestResultDTO lastRun) { this.lastRun = lastRun; }
}
