package com.docgen.dto;

import java.time.Instant;

/**
 * Excludes sensitive authentication fields (password never included).
 */
public class UserDTO {

    private Long id;
    private Long tenantId;
    private String username;
    private String email;
    private String role;
    private Long teamId;
    private String languagePreference;
    private Instant createdAt;

    public UserDTO() {
    }

    public UserDTO(Long id, Long tenantId, String username, String email,
                   String role, Long teamId, String languagePreference, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.teamId = teamId;
        this.languagePreference = languagePreference;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
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

    public String getLanguagePreference() {
        return languagePreference;
    }

    public void setLanguagePreference(String languagePreference) {
        this.languagePreference = languagePreference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
