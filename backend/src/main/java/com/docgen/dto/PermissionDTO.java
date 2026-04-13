package com.docgen.dto;

import com.docgen.entity.PermissionType;

import java.time.Instant;

/**
 * Response DTO for permission information.
 */
public class PermissionDTO {

    private Long id;
    private Long templateId;
    private Long userId;
    private Long teamId;
    private PermissionType permissionType;
    private Instant grantedAt;
    private Long grantedBy;

    public PermissionDTO() {}

    public PermissionDTO(Long id, Long templateId, Long userId, Long teamId,
                         PermissionType permissionType, Instant grantedAt, Long grantedBy) {
        this.id = id;
        this.templateId = templateId;
        this.userId = userId;
        this.teamId = teamId;
        this.permissionType = permissionType;
        this.grantedAt = grantedAt;
        this.grantedBy = grantedBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public PermissionType getPermissionType() { return permissionType; }
    public void setPermissionType(PermissionType permissionType) { this.permissionType = permissionType; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }

    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }
}
