package com.docgen.service;

import com.docgen.dto.CreateTeamRequest;
import com.docgen.dto.TeamDTO;
import com.docgen.dto.UpdateTeamRequest;
import com.docgen.entity.Team;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TeamRepository;
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

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Transactional
    public TeamDTO createTeam(CreateTeamRequest request) {
        if (teamRepository.existsByTenantIdAndName(request.getTenantId(), request.getName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "团队名称在该租户下已存在", HttpStatus.CONFLICT);
        }

        Team team = new Team();
        team.setTenantId(request.getTenantId());
        team.setName(request.getName());
        team.setDescription(request.getDescription());

        Team saved = teamRepository.save(team);
        log.info("Team created: name={}, tenantId={}", saved.getName(), saved.getTenantId());
        return toDTO(saved);
    }

    @Transactional
    public TeamDTO updateTeam(Long id, UpdateTeamRequest request) {
        Team team = findTeamOrThrow(id);

        if (request.getName() != null && !request.getName().equals(team.getName())) {
            if (teamRepository.existsByTenantIdAndName(team.getTenantId(), request.getName())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "团队名称在该租户下已存在", HttpStatus.CONFLICT);
            }
            team.setName(request.getName());
        }
        if (request.getDescription() != null) {
            team.setDescription(request.getDescription());
        }

        Team saved = teamRepository.save(team);
        log.info("Team updated: id={}", saved.getId());
        return toDTO(saved);
    }

    @Transactional
    public void deleteTeam(Long id) {
        if (!teamRepository.existsById(id)) {
            throw new ResourceNotFoundException(ErrorCode.VALIDATION_FAILED, "团队不存在");
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
    public TeamDTO getTeamById(Long id) {
        return toDTO(findTeamOrThrow(id));
    }


    private Team findTeamOrThrow(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_FAILED, "团队不存在"));
    }

    private TeamDTO toDTO(Team team) {
        return new TeamDTO(
                team.getId(),
                team.getTenantId(),
                team.getName(),
                team.getDescription(),
                team.getCreatedAt()
        );
    }
}
