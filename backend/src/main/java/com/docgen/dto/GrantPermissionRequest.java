package com.docgen.dto;

import com.docgen.entity.PermissionType;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for granting a permission on a template.
 * Either userId or teamId must be provided (not both null).
 */
public class GrantPermissionRequest {

    private Long userId;

    private Long teamId;

    @NotNull(message = "权限类型不能为空")
    private PermissionType permissionType;

    public GrantPermissionRequest() {}

    public GrantPermissionRequest(Long userId, Long teamId, PermissionType permissionType) {
        this.userId = userId;
        this.teamId = teamId;
        this.permissionType = permissionType;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public PermissionType getPermissionType() { return permissionType; }
    public void setPermissionType(PermissionType permissionType) { this.permissionType = permissionType; }
}
