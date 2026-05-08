package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateTeamRequest {

    @NotNull(message = "tenantId is required")
    private Long tenantId;

    @NotBlank(message = "Team name is required")
    private String name;

    private String description;

    @Pattern(regexp = "CROSS_REVIEW|MAKER_CHECKER", message = "approvalMode must be CROSS_REVIEW or MAKER_CHECKER")
    private String approvalMode;

    @Size(max = 128)
    private String adGroupObjectId;

    @Size(max = 128)
    private String adMakerGroupObjectId;

    @Size(max = 128)
    private String adCheckerGroupObjectId;

    public CreateTeamRequest() {}

    public CreateTeamRequest(Long tenantId, String name, String description) {
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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
