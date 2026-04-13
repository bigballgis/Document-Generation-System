package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new team.
 */
public class CreateTeamRequest {

    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    @NotBlank(message = "团队名称不能为空")
    private String name;

    private String description;

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
}
