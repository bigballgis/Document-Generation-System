package com.docgen.dto;

import java.time.Instant;
import java.util.List;

/**
 * Report DTO for running all tests on a composite template.
 */
public class CompositeTestReportDTO {

    private Long compositeTemplateId;
    private String templateName;
    private List<SegmentTestResultDTO> segmentResults;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private Instant executedAt;

    public Long getCompositeTemplateId() { return compositeTemplateId; }
    public void setCompositeTemplateId(Long compositeTemplateId) { this.compositeTemplateId = compositeTemplateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public List<SegmentTestResultDTO> getSegmentResults() { return segmentResults; }
    public void setSegmentResults(List<SegmentTestResultDTO> segmentResults) { this.segmentResults = segmentResults; }

    public int getTotalTests() { return totalTests; }
    public void setTotalTests(int totalTests) { this.totalTests = totalTests; }

    public int getPassedTests() { return passedTests; }
    public void setPassedTests(int passedTests) { this.passedTests = passedTests; }

    public int getFailedTests() { return failedTests; }
    public void setFailedTests(int failedTests) { this.failedTests = failedTests; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
