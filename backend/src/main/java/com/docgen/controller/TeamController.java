package com.docgen.controller;

import com.docgen.dto.CreateTeamRequest;
import com.docgen.dto.TeamDTO;
import com.docgen.dto.UpdateTeamRequest;
import com.docgen.dto.UserPrincipal;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamDTO> createTeam(
            @PathVariable Long tenantId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTeamRequest request) {
        assertTenantAccess(principal, tenantId);
        request.setTenantId(tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(request));
    }

    @GetMapping
    public ResponseEntity<List<TeamDTO>> listTeams(
            @PathVariable Long tenantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        assertTenantAccess(principal, tenantId);
        return ResponseEntity.ok(teamService.listTeams(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamDTO> getTeam(
            @PathVariable Long tenantId,
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        assertTenantAccess(principal, tenantId);
        return ResponseEntity.ok(teamService.getTeamById(tenantId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamDTO> updateTeam(
            @PathVariable Long tenantId,
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateTeamRequest request) {
        assertTenantAccess(principal, tenantId);
        return ResponseEntity.ok(teamService.updateTeam(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable Long tenantId,
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        assertTenantAccess(principal, tenantId);
        teamService.deleteTeam(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    private static void assertTenantAccess(UserPrincipal principal, Long tenantId) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN,
                    "Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        if ("SUPER_ADMIN".equals(principal.getRole())) {
            return;
        }
        if ("TENANT_ADMIN".equals(principal.getRole())
                && tenantId.equals(principal.getTenantId())) {
            return;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Not allowed to manage teams for this tenant", HttpStatus.FORBIDDEN);
    }
}
