package com.docgen.dto;

import java.util.Map;

public class GenerateDocumentRequest {

    /** Runtime parameters passed to data sources and expressions. */
    private Map<String, Object> parameters;

    /** Output format for this request: WORD or PDF only. BOTH is not supported (call /word and /pdf separately). */
    private String outputFormat;

    /** Storage strategy override: TEMP or PERSISTENT. Defaults to template's configured strategy. */
    private String storageStrategy;

    public GenerateDocumentRequest() {}

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }
}
