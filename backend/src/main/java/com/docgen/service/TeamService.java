package com.docgen.service;

import com.docgen.dto.CreateTeamRequest;
import com.docgen.dto.TeamDTO;
import com.docgen.dto.UpdateTeamRequest;
import com.docgen.entity.Team;
import com.docgen.entity.TeamApprovalMode;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TeamRepository;
import com.docgen.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service handling team CRUD within a tenant scope.
 */
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TeamDTO createTeam(CreateTeamRequest request) {
        if (teamRepository.existsByTenantIdAndName(request.getTenantId(), request.getName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Team name already exists for this tenant", HttpStatus.CONFLICT);
        }

        Team team = new Team();
        team.setTenantId(request.getTenantId());
        team.setName(request.getName());
        team.setDescription(request.getDescription());

        TeamApprovalMode mode = resolveApprovalMode(request.getApprovalMode());
        applyAdBindings(team, mode,
                request.getAdGroupObjectId(),
                request.getAdMakerGroupObjectId(),
                request.getAdCheckerGroupObjectId());

        Team saved = teamRepository.save(team);
        log.info("Team created: name={}, tenantId={}, mode={}", saved.getName(), saved.getTenantId(), saved.getApprovalMode());
        return toDTO(saved);
    }

    @Transactional
    public TeamDTO updateTeam(Long tenantId, Long id, UpdateTeamRequest request) {
        Team team = findTeamInTenantOrThrow(tenantId, id);
        TeamApprovalMode previousMode = team.getApprovalMode();

        if (request.getName() != null && !request.getName().equals(team.getName())) {
            if (teamRepository.existsByTenantIdAndName(team.getTenantId(), request.getName())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Team name already exists for this tenant", HttpStatus.CONFLICT);
            }
            team.setName(request.getName());
        }
        if (request.getDescription() != null) {
            team.setDescription(request.getDescription());
        }

        if (request.getApprovalMode() != null) {
            TeamApprovalMode mode = resolveApprovalMode(request.getApprovalMode());
            applyAdBindings(team, mode,
                    request.getAdGroupObjectId(),
                    request.getAdMakerGroupObjectId(),
                    request.getAdCheckerGroupObjectId());
        }

        if (team.getApprovalMode() == TeamApprovalMode.MAKER_CHECKER
                && previousMode != TeamApprovalMode.MAKER_CHECKER
                && userRepository.existsByTeamIdWithMissingReviewLane(id)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Cannot enable maker-checker mode until every team member has teamReviewLane "
                            + "set to MAKER or CHECKER in user administration.",
                    HttpStatus.CONFLICT);
        }

        Team saved = teamRepository.save(team);
        log.info("Team updated: id={}", saved.getId());
        return toDTO(saved);
    }

    @Transactional
    public void deleteTeam(Long tenantId, Long id) {
        findTeamInTenantOrThrow(tenantId, id);
        if (userRepository.existsByTeamId(id)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Cannot delete this team while users are still assigned to it. "
                            + "Reassign or remove those users first.",
                    HttpStatus.CONFLICT);
        }
        teamRepository.deleteById(id);
        log.info("Team deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<TeamDTO> listTeams(Long tenantId) {
        return teamRepository.findByTenantId(tenantId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TeamDTO getTeamById(Long tenantId, Long id) {
        return toDTO(findTeamInTenantOrThrow(tenantId, id));
    }

    private Team findTeamOrThrow(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_FAILED, "Team not found"));
    }

    private Team findTeamInTenantOrThrow(Long tenantId, Long teamId) {
        Team team = findTeamOrThrow(teamId);
        if (!team.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException(ErrorCode.VALIDATION_FAILED, "Team not found");
        }
        return team;
    }
    private static TeamApprovalMode resolveApprovalMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return TeamApprovalMode.CROSS_REVIEW;
        }
        return TeamApprovalMode.valueOf(raw.trim());
    }

    static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Validates AD group binding rules and writes fields on the entity.
     * <ul>
     *   <li>CROSS_REVIEW: optional single membership group; maker/checker must be unset.</li>
     *   <li>MAKER_CHECKER: maker and checker groups required; membership group must be unset.</li>
     * </ul>
     */
    static void applyAdBindings(Team team, TeamApprovalMode mode,
                                  String adGroupObjectId, String adMakerGroupObjectId, String adCheckerGroupObjectId) {
        String adGroup = trimToNull(adGroupObjectId);
        String adMaker = trimToNull(adMakerGroupObjectId);
        String adChecker = trimToNull(adCheckerGroupObjectId);
        team.setApprovalMode(mode);
        if (mode == TeamApprovalMode.CROSS_REVIEW) {
            if (adMaker != null || adChecker != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Maker and checker Azure AD group IDs must be empty for cross-review teams",
                        HttpStatus.BAD_REQUEST);
            }
            team.setAdGroupObjectId(adGroup);
            team.setAdMakerGroupObjectId(null);
            team.setAdCheckerGroupObjectId(null);
        } else {
            if (adGroup != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Membership Azure AD group ID is only used in cross-review mode",
                        HttpStatus.BAD_REQUEST);
            }
            if (adMaker == null || adChecker == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Maker-checker teams require both maker and checker Azure AD group object IDs",
                        HttpStatus.BAD_REQUEST);
            }
            team.setAdGroupObjectId(null);
            team.setAdMakerGroupObjectId(adMaker);
            team.setAdCheckerGroupObjectId(adChecker);
        }
    }

    private TeamDTO toDTO(Team team) {
        TeamDTO dto = new TeamDTO(
                team.getId(),
                team.getTenantId(),
                team.getName(),
                team.getDescription(),
                team.getCreatedAt());
        if (team.getApprovalMode() != null) {
            dto.setApprovalMode(team.getApprovalMode().name());
        }
        dto.setAdGroupObjectId(team.getAdGroupObjectId());
        dto.setAdMakerGroupObjectId(team.getAdMakerGroupObjectId());
        dto.setAdCheckerGroupObjectId(team.getAdCheckerGroupObjectId());
        return dto;
    }
}
