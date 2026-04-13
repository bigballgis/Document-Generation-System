package com.docgen.dto;

import java.util.Map;

/**
 * Request DTO for template preview.
 */
public class PreviewRequest {

    /** User-provided test data for preview. */
    private Map<String, Object> testData;

    /** Whether to use real data sources instead of test data. */
    private boolean useRealDataSources;

    /** Runtime parameters for real data source calls. */
    private Map<String, Object> parameters;

    /** Output format: WORD or PDF. Defaults to WORD. */
    private String outputFormat;

    /** Whether to return an OnlyOffice editor URL for in-browser preview. */
    private boolean onlyOfficePreview;

    public PreviewRequest() {}

    public Map<String, Object> getTestData() { return testData; }
    public void setTestData(Map<String, Object> testData) { this.testData = testData; }

    public boolean isUseRealDataSources() { return useRealDataSources; }
    public void setUseRealDataSources(boolean useRealDataSources) { this.useRealDataSources = useRealDataSources; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public boolean isOnlyOfficePreview() { return onlyOfficePreview; }
    public void setOnlyOfficePreview(boolean onlyOfficePreview) { this.onlyOfficePreview = onlyOfficePreview; }
}
