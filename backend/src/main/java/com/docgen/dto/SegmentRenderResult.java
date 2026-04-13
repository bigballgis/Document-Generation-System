package com.docgen.dto;

/**
 * Result of rendering a single segment, including timing and error information.
 */
public class SegmentRenderResult {

    private Long segmentId;
    private String segmentName;
    private boolean success;
    private long renderTimeMs;
    private String errorMessage;

    /**
     * The rendered .docx bytes. Excluded from JSON serialization — used internally
     * by AssemblyEngineService for merging.
     */
    private transient byte[] renderedBytes;

    public SegmentRenderResult() {}

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public String getSegmentName() { return segmentName; }
    public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public long getRenderTimeMs() { return renderTimeMs; }
    public void setRenderTimeMs(long renderTimeMs) { this.renderTimeMs = renderTimeMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public byte[] getRenderedBytes() { return renderedBytes; }
    public void setRenderedBytes(byte[] renderedBytes) { this.renderedBytes = renderedBytes; }
}
