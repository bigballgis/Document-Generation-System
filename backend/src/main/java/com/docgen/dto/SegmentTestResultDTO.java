package com.docgen.dto;

/**
 * Result DTO for a single segment test execution.
 */
public class SegmentTestResultDTO {

    private Long segmentId;
    private Long testDataId;
    private String testDataName;
    private boolean success;
    private long renderTimeMs;
    private String errorMessage;

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Long getTestDataId() { return testDataId; }
    public void setTestDataId(Long testDataId) { this.testDataId = testDataId; }

    public String getTestDataName() { return testDataName; }
    public void setTestDataName(String testDataName) { this.testDataName = testDataName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public long getRenderTimeMs() { return renderTimeMs; }
    public void setRenderTimeMs(long renderTimeMs) { this.renderTimeMs = renderTimeMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
