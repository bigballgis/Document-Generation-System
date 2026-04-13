package com.docgen.dto;

import java.time.Instant;

/**
 * DTO representing a single data point in the API call trend chart.
 */
public class ApiCallMetricDTO {

    private Instant timestamp;
    private long callCount;
    private double avgResponseTimeMs;

    public ApiCallMetricDTO() {}

    public ApiCallMetricDTO(Instant timestamp, long callCount, double avgResponseTimeMs) {
        this.timestamp = timestamp;
        this.callCount = callCount;
        this.avgResponseTimeMs = avgResponseTimeMs;
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public long getCallCount() { return callCount; }
    public void setCallCount(long callCount) { this.callCount = callCount; }

    public double getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(double avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }
}
