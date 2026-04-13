package com.docgen.dto;

import com.docgen.entity.TestStatus;

import java.time.Instant;

/**
 * Response DTO for a test case execution result.
 */
public class TestResultDTO {

    private Long id;
    private Long testCaseId;
    private TestStatus status;
    private String actualResultJson;
    private String diffDetails;
    private Instant executedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTestCaseId() { return testCaseId; }
    public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }

    public TestStatus getStatus() { return status; }
    public void setStatus(TestStatus status) { this.status = status; }

    public String getActualResultJson() { return actualResultJson; }
    public void setActualResultJson(String actualResultJson) { this.actualResultJson = actualResultJson; }

    public String getDiffDetails() { return diffDetails; }
    public void setDiffDetails(String diffDetails) { this.diffDetails = diffDetails; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
