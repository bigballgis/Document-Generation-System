package com.docgen.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal carrying the authenticated user's identity.
 */
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final Long tenantId;
    private final String role;
    private final Long teamId;
    private final String username;

    public UserPrincipal(Long userId, Long tenantId, String role, Long teamId, String username) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role;
        this.teamId = teamId;
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getRole() {
        return role;
    }

    public Long getTeamId() {
        return teamId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
