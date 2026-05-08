package com.docgen.dto;

import com.docgen.entity.TestStatus;

import java.time.Instant;

public class TestResultDTO {

    private Long id;
    private Long testCaseId;
    /** Display name of the test case (denormalized for report tables). */
    private String testCaseName;
    private TestStatus status;
    private String actualResultJson;
    private String diffDetails;
    private Instant executedAt;
    /** Populated when the trial stored a sample rendered document (Word / DOCX). */
    private Long sampleDocumentId;
    private String sampleDocumentDownloadUrl;

    /** Populated when the trial stored a PDF sample alongside the DOCX sample. */
    private Long samplePdfDocumentId;
    private String samplePdfDocumentDownloadUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTestCaseId() { return testCaseId; }
    public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }

    public String getTestCaseName() { return testCaseName; }
    public void setTestCaseName(String testCaseName) { this.testCaseName = testCaseName; }

    public TestStatus getStatus() { return status; }
    public void setStatus(TestStatus status) { this.status = status; }

    public String getActualResultJson() { return actualResultJson; }
    public void setActualResultJson(String actualResultJson) { this.actualResultJson = actualResultJson; }

    public String getDiffDetails() { return diffDetails; }
    public void setDiffDetails(String diffDetails) { this.diffDetails = diffDetails; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }

    public Long getSampleDocumentId() { return sampleDocumentId; }
    public void setSampleDocumentId(Long sampleDocumentId) { this.sampleDocumentId = sampleDocumentId; }

    public String getSampleDocumentDownloadUrl() { return sampleDocumentDownloadUrl; }
    public void setSampleDocumentDownloadUrl(String sampleDocumentDownloadUrl) { this.sampleDocumentDownloadUrl = sampleDocumentDownloadUrl; }

    public Long getSamplePdfDocumentId() { return samplePdfDocumentId; }
    public void setSamplePdfDocumentId(Long samplePdfDocumentId) { this.samplePdfDocumentId = samplePdfDocumentId; }

    public String getSamplePdfDocumentDownloadUrl() { return samplePdfDocumentDownloadUrl; }
    public void setSamplePdfDocumentDownloadUrl(String samplePdfDocumentDownloadUrl) { this.samplePdfDocumentDownloadUrl = samplePdfDocumentDownloadUrl; }
}
