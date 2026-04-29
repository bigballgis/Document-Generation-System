package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Tenant / super admin user updates (role, team assignment, maker-checker lane).
 */
public class AdminUserUpdateRequest {

    @NotBlank
    private String role;

    private Long teamId;

    /**
     * MAKER, CHECKER, or null / omitted (see {@code UserService#updateUserAdmin} semantics).
     */
    private String teamReviewLane;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getTeamReviewLane() {
        return teamReviewLane;
    }

    public void setTeamReviewLane(String teamReviewLane) {
        this.teamReviewLane = teamReviewLane;
    }
}
