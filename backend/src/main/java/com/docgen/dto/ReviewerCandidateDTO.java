package com.docgen.dto;

/**
 * User eligible to review a template within the same tenant and team (excludes template author in list endpoint).
 */
public class ReviewerCandidateDTO {

    private Long id;
    private String username;
    private String email;
    private Long teamId;
    private String role;

    public ReviewerCandidateDTO() {
    }

    public ReviewerCandidateDTO(Long id, String username, String email, Long teamId, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.teamId = teamId;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
