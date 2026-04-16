package com.docgen.dto;

/**
 * DTO representing the health status of a data source (DB, Redis, MinIO).
 */
public class DataSourceHealthDTO {

    private String name;
    private String type;
    private boolean reachable;
    private double avgResponseTimeMs;
    private String lastError;

    public DataSourceHealthDTO() {}

    public DataSourceHealthDTO(String name, String type, boolean reachable, double avgResponseTimeMs, String lastError) {
        this.name = name;
        this.type = type;
        this.reachable = reachable;
        this.avgResponseTimeMs = avgResponseTimeMs;
        this.lastError = lastError;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isReachable() { return reachable; }
    public void setReachable(boolean reachable) { this.reachable = reachable; }

    public double getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(double avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
