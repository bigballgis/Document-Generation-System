package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for tenant information.
 */
public class TenantDTO {

    private Long id;
    private String name;
    private String contactName;
    private String contactEmail;
    private String status;
    private int maxTemplates;
    private long maxApiCallsMonthly;
    private long maxStorageBytes;
    private Instant createdAt;
    private Instant updatedAt;

    public TenantDTO() {}

    public TenantDTO(Long id, String name, String contactName, String contactEmail,
                     String status, int maxTemplates, long maxApiCallsMonthly,
                     long maxStorageBytes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.status = status;
        this.maxTemplates = maxTemplates;
        this.maxApiCallsMonthly = maxApiCallsMonthly;
        this.maxStorageBytes = maxStorageBytes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getMaxTemplates() { return maxTemplates; }
    public void setMaxTemplates(int maxTemplates) { this.maxTemplates = maxTemplates; }

    public long getMaxApiCallsMonthly() { return maxApiCallsMonthly; }
    public void setMaxApiCallsMonthly(long maxApiCallsMonthly) { this.maxApiCallsMonthly = maxApiCallsMonthly; }

    public long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
