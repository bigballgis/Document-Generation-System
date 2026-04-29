package com.docgen.service;

import com.docgen.dto.CreateTeamRequest;
import com.docgen.dto.UpdateTeamRequest;
import com.docgen.entity.Team;
import com.docgen.entity.TeamApprovalMode;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TeamRepository;
import com.docgen.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, userRepository);
    }

    @Test
    void deleteTeam_whenUsersAssigned_throwsAndDoesNotDelete() {
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(1L);
        team.setName("Alpha");
        team.setCreatedAt(Instant.now());
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.existsByTeamId(5L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> teamService.deleteTeam(1L, 5L));

        verify(teamRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteTeam_whenNoUsers_deletes() {
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(1L);
        team.setName("Alpha");
        team.setCreatedAt(Instant.now());
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.existsByTeamId(5L)).thenReturn(false);

        teamService.deleteTeam(1L, 5L);

        verify(teamRepository).deleteById(5L);
    }

    @Test
    void deleteTeam_wrongTenant_throwsNotFound() {
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(2L);
        team.setName("Alpha");
        team.setCreatedAt(Instant.now());
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        assertThrows(ResourceNotFoundException.class, () -> teamService.deleteTeam(1L, 5L));

        verify(userRepository, never()).existsByTeamId(anyLong());
        verify(teamRepository, never()).deleteById(anyLong());
    }

    @Test
    void updateTeam_whenSwitchingToMakerChecker_andMemberLacksLane_throws() {
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(1L);
        team.setName("Alpha");
        team.setApprovalMode(TeamApprovalMode.CROSS_REVIEW);
        team.setCreatedAt(Instant.now());
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.existsByTeamIdWithMissingReviewLane(5L)).thenReturn(true);

        UpdateTeamRequest req = new UpdateTeamRequest();
        req.setApprovalMode("MAKER_CHECKER");
        req.setAdMakerGroupObjectId("00000000-0000-0000-0000-000000000001");
        req.setAdCheckerGroupObjectId("00000000-0000-0000-0000-000000000002");

        assertThrows(BusinessException.class, () -> teamService.updateTeam(1L, 5L, req));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_whenSwitchingToMakerChecker_andAllMembersHaveLane_saves() {
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(1L);
        team.setName("Alpha");
        team.setApprovalMode(TeamApprovalMode.CROSS_REVIEW);
        team.setCreatedAt(Instant.now());
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.existsByTeamIdWithMissingReviewLane(5L)).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTeamRequest req = new UpdateTeamRequest();
        req.setApprovalMode("MAKER_CHECKER");
        req.setAdMakerGroupObjectId("00000000-0000-0000-0000-000000000001");
        req.setAdCheckerGroupObjectId("00000000-0000-0000-0000-000000000002");

        teamService.updateTeam(1L, 5L, req);

        verify(teamRepository).save(any(Team.class));
    }

    @Test
    void createTeam_duplicateName_throws() {
        when(teamRepository.existsByTenantIdAndName(1L, "Dup")).thenReturn(true);
        CreateTeamRequest req = new CreateTeamRequest(1L, "Dup", null);
        assertThrows(BusinessException.class, () -> teamService.createTeam(req));
        verify(teamRepository, never()).save(any());
    }
}
