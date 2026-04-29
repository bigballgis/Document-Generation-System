package com.docgen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageStatsDTO {

    private Long tenantId;
    private long monthlyQuota;
    private long currentMonthUsage;
    private long remainingQuota;
    private String resetAt;


    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public long getMonthlyQuota() { return monthlyQuota; }
    public void setMonthlyQuota(long monthlyQuota) { this.monthlyQuota = monthlyQuota; }

    public long getCurrentMonthUsage() { return currentMonthUsage; }
    public void setCurrentMonthUsage(long currentMonthUsage) { this.currentMonthUsage = currentMonthUsage; }

    public long getRemainingQuota() { return remainingQuota; }
    public void setRemainingQuota(long remainingQuota) { this.remainingQuota = remainingQuota; }

    public String getResetAt() { return resetAt; }
    public void setResetAt(String resetAt) { this.resetAt = resetAt; }
}

