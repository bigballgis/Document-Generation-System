package com.docgen.dto;

public class TenantUsageDTO {

    private long currentTemplateCount;
    private long currentMonthApiCalls;
    private long usedStorageBytes;
    private int maxTemplates;
    private long maxApiCallsMonthly;
    private long maxStorageBytes;

    public TenantUsageDTO() {}

    public TenantUsageDTO(long currentTemplateCount, long currentMonthApiCalls,
                          long usedStorageBytes, int maxTemplates,
                          long maxApiCallsMonthly, long maxStorageBytes) {
        this.currentTemplateCount = currentTemplateCount;
        this.currentMonthApiCalls = currentMonthApiCalls;
        this.usedStorageBytes = usedStorageBytes;
        this.maxTemplates = maxTemplates;
        this.maxApiCallsMonthly = maxApiCallsMonthly;
        this.maxStorageBytes = maxStorageBytes;
    }

    public long getCurrentTemplateCount() { return currentTemplateCount; }
    public void setCurrentTemplateCount(long currentTemplateCount) { this.currentTemplateCount = currentTemplateCount; }

    public long getCurrentMonthApiCalls() { return currentMonthApiCalls; }
    public void setCurrentMonthApiCalls(long currentMonthApiCalls) { this.currentMonthApiCalls = currentMonthApiCalls; }

    public long getUsedStorageBytes() { return usedStorageBytes; }
    public void setUsedStorageBytes(long usedStorageBytes) { this.usedStorageBytes = usedStorageBytes; }

    public int getMaxTemplates() { return maxTemplates; }
    public void setMaxTemplates(int maxTemplates) { this.maxTemplates = maxTemplates; }

    public long getMaxApiCallsMonthly() { return maxApiCallsMonthly; }
    public void setMaxApiCallsMonthly(long maxApiCallsMonthly) { this.maxApiCallsMonthly = maxApiCallsMonthly; }

    public long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
}
