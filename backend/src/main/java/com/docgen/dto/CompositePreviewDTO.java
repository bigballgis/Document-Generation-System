package com.docgen.dto;

import java.util.List;

/**
 * Preview URL plus per-segment preview status entries.
 */
public class CompositePreviewDTO {

    private String previewUrl;
    private List<SegmentPreviewEntry> segmentPreviews;

    public CompositePreviewDTO() {}

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public List<SegmentPreviewEntry> getSegmentPreviews() { return segmentPreviews; }
    public void setSegmentPreviews(List<SegmentPreviewEntry> segmentPreviews) { this.segmentPreviews = segmentPreviews; }

    /**
     * Preview status entry for a single segment within a composite preview.
     */
    public static class SegmentPreviewEntry {

        private String segmentName;
        private String status;
        private String errorMessage;

        public SegmentPreviewEntry() {}

        public String getSegmentName() { return segmentName; }
        public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
