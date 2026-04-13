package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for selective preview of a Composite_Template.
 * Allows the user to specify a subset of segment IDs to preview.
 */
public class SelectivePreviewRequest {

    @NotEmpty(message = "至少选择一个段落进行预览")
    private List<Long> segmentIds;

    private Map<String, Object> testData;

    public SelectivePreviewRequest() {}

    public List<Long> getSegmentIds() { return segmentIds; }
    public void setSegmentIds(List<Long> segmentIds) { this.segmentIds = segmentIds; }

    public Map<String, Object> getTestData() { return testData; }
    public void setTestData(Map<String, Object> testData) { this.testData = testData; }
}
