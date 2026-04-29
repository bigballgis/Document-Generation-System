package com.docgen.dto;

import java.time.Instant;

public class TeamDTO {

    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private String approvalMode;
    private String adGroupObjectId;
    private String adMakerGroupObjectId;
    private String adCheckerGroupObjectId;
    private Instant createdAt;

    public TeamDTO() {}

    public TeamDTO(Long id, Long tenantId, String name, String description, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getApprovalMode() {
        return approvalMode;
    }

    public void setApprovalMode(String approvalMode) {
        this.approvalMode = approvalMode;
    }

    public String getAdGroupObjectId() {
        return adGroupObjectId;
    }

    public void setAdGroupObjectId(String adGroupObjectId) {
        this.adGroupObjectId = adGroupObjectId;
    }

    public String getAdMakerGroupObjectId() {
        return adMakerGroupObjectId;
    }

    public void setAdMakerGroupObjectId(String adMakerGroupObjectId) {
        this.adMakerGroupObjectId = adMakerGroupObjectId;
    }

    public String getAdCheckerGroupObjectId() {
        return adCheckerGroupObjectId;
    }

    public void setAdCheckerGroupObjectId(String adCheckerGroupObjectId) {
        this.adCheckerGroupObjectId = adCheckerGroupObjectId;
    }
}
