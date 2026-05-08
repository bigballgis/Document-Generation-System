package com.docgen.dto;

/**
 * Per-segment render time statistic included in composite document generation responses.
 * Used for performance analysis and webhook notifications.
 */
public class SegmentRenderStat {

    private String segmentName;
    private long renderTimeMs;
    private boolean success;
    private String errorMessage;

    public SegmentRenderStat() {}

    public String getSegmentName() { return segmentName; }
    public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

    public long getRenderTimeMs() { return renderTimeMs; }
    public void setRenderTimeMs(long renderTimeMs) { this.renderTimeMs = renderTimeMs; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
