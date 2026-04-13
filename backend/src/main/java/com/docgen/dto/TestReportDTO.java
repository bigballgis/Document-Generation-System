package com.docgen.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated test report for all test cases of a template.
 */
public class TestReportDTO {

    private Long templateId;
    private int totalCount;
    private int passedCount;
    private int failedCount;
    private List<TestResultDTO> results;
    private Instant executedAt;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getPassedCount() { return passedCount; }
    public void setPassedCount(int passedCount) { this.passedCount = passedCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public List<TestResultDTO> getResults() { return results; }
    public void setResults(List<TestResultDTO> results) { this.results = results; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
