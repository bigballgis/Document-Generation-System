package com.docgen.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code rate_limit_configs} table.
 * Stores per-tenant monthly API call quota and usage tracking.
 */
@Entity
@Table(name = "rate_limit_configs")
public class RateLimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    @Column(name = "monthly_quota", nullable = false)
    private long monthlyQuota = 100000L;

    @Column(name = "current_month_usage", nullable = false)
    private long currentMonthUsage = 0L;

    @Column(name = "reset_at", nullable = false)
    private Instant resetAt;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public long getMonthlyQuota() { return monthlyQuota; }
    public void setMonthlyQuota(long monthlyQuota) { this.monthlyQuota = monthlyQuota; }

    public long getCurrentMonthUsage() { return currentMonthUsage; }
    public void setCurrentMonthUsage(long currentMonthUsage) { this.currentMonthUsage = currentMonthUsage; }

    public Instant getResetAt() { return resetAt; }
    public void setResetAt(Instant resetAt) { this.resetAt = resetAt; }
}

