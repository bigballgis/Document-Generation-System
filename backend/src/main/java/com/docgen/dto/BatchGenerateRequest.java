package com.docgen.dto;

import java.util.List;
import java.util.Map;

public class BatchGenerateRequest {

    /** List of data sets, each producing one document. Max 1000. */
    private List<Map<String, Object>> dataSets;

    /** Output format: WORD, PDF, or BOTH. */
    private String outputFormat;

    /** Storage strategy: TEMP or PERSISTENT. */
    private String storageStrategy;

    /**
     * Failure strategy: CONTINUE (default) or THRESHOLD.
     * CONTINUE: keep generating remaining documents on failure.
     * THRESHOLD: stop when failure count reaches failureThreshold.
     */
    private String failureStrategy = "CONTINUE";

    /** Max failures before stopping (only used when failureStrategy=THRESHOLD). */
    private int failureThreshold = 100;

    public BatchGenerateRequest() {}

    public List<Map<String, Object>> getDataSets() { return dataSets; }
    public void setDataSets(List<Map<String, Object>> dataSets) { this.dataSets = dataSets; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }

    public String getFailureStrategy() { return failureStrategy; }
    public void setFailureStrategy(String failureStrategy) { this.failureStrategy = failureStrategy; }

    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
}
