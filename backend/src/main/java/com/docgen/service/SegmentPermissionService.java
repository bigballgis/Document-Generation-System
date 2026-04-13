package com.docgen.service;

import com.docgen.dto.GrantPermissionRequest;
import com.docgen.dto.PermissionDTO;
import com.docgen.entity.Permission;
import com.docgen.entity.PermissionType;
import com.docgen.entity.Segment;
import com.docgen.exception.AccessDeniedException;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.PermissionRepository;
import com.docgen.repository.SegmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service handling segment-level permission management.
 * Reuses the existing Permission entity with resource_type = 'SEGMENT'
 * and resource_id = segmentId.
 */
@Service
public class SegmentPermissionService {

    private static final Logger log = LoggerFactory.getLogger(SegmentPermissionService.class);
    private static final String RESOURCE_TYPE_SEGMENT = "SEGMENT";

    private final PermissionRepository permissionRepository;
    private final SegmentRepository segmentRepository;

    public SegmentPermissionService(PermissionRepository permissionRepository,
                                    SegmentRepository segmentRepository) {
        this.permissionRepository = permissionRepository;
        this.segmentRepository = segmentRepository;
    }

    /**
     * Check whether a user has a specific permission on a segment.
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, Long teamId, Long segmentId, PermissionType type) {
        if (permissionRepository.existsByResourceTypeAndResourceIdAndUserIdAndPermissionType(
                RESOURCE_TYPE_SEGMENT, segmentId, userId, type)) {
            return true;
        }
        if (teamId != null) {
            return permissionRepository.existsByResourceTypeAndResourceIdAndTeamIdAndPermissionType(
                    RESOURCE_TYPE_SEGMENT, segmentId, teamId, type);
        }
        return false;
    }

    /**
     * Verify that a user has the required permission on a segment.
     * Segment creator and TENANT_ADMIN/SUPER_ADMIN bypass permission checks.
     * Throws AccessDeniedException (403) if the user lacks the permission.
     */
    @Transactional(readOnly = true)
    public void checkPermission(Long userId, Long teamId, String role, Long segmentId, PermissionType type) {
        if ("SUPER_ADMIN".equals(role) || "TENANT_ADMIN".equals(role)) {
            return;
        }

        Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SEGMENT_NOT_FOUND, "段落不存在"));

        // Segment creator has all permissions
        if (userId.equals(segment.getCreatedBy())) {
            return;
        }

        if (!hasPermission(userId, teamId, segmentId, type)) {
            throw new AccessDeniedException("无权执行此操作，需要 " + type + " 权限");
        }
    }

    /**
     * Grant a permission on a segment.
     */
    @Transactional
    public PermissionDTO grantPermission(Long segmentId, GrantPermissionRequest request, Long grantedBy) {
        validateGrantRequest(request);
        verifySegmentExists(segmentId);

        // Check for duplicate
        if (request.getUserId() != null &&
                permissionRepository.existsByResourceTypeAndResourceIdAndUserIdAndPermissionType(
                        RESOURCE_TYPE_SEGMENT, segmentId, request.getUserId(), request.getPermissionType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "该用户已拥有此权限", HttpStatus.CONFLICT);
        }
        if (request.getTeamId() != null &&
                permissionRepository.existsByResourceTypeAndResourceIdAndTeamIdAndPermissionType(
                        RESOURCE_TYPE_SEGMENT, segmentId, request.getTeamId(), request.getPermissionType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "该团队已拥有此权限", HttpStatus.CONFLICT);
        }

        Permission permission = new Permission();
        permission.setTemplateId(0L); // Not template-scoped
        permission.setResourceType(RESOURCE_TYPE_SEGMENT);
        permission.setResourceId(segmentId);
        permission.setUserId(request.getUserId());
        permission.setTeamId(request.getTeamId());
        permission.setPermissionType(request.getPermissionType());
        permission.setGrantedBy(grantedBy);

        Permission saved = permissionRepository.save(permission);
        log.info("Segment permission granted: segmentId={}, userId={}, teamId={}, type={}",
                segmentId, request.getUserId(), request.getTeamId(), request.getPermissionType());
        return toDTO(saved);
    }

    /**
     * Revoke a specific permission by its ID.
     */
    @Transactional
    public void revokePermission(Long segmentId, Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_FAILED, "权限记录不存在"));

        if (!RESOURCE_TYPE_SEGMENT.equals(permission.getResourceType())
                || !segmentId.equals(permission.getResourceId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "权限记录不属于该段落", HttpStatus.BAD_REQUEST);
        }

        permissionRepository.delete(permission);
        log.info("Segment permission revoked: id={}, segmentId={}", permissionId, segmentId);
    }

    /**
     * List all permissions for a segment.
     */
    @Transactional(readOnly = true)
    public List<PermissionDTO> getSegmentPermissions(Long segmentId) {
        verifySegmentExists(segmentId);
        return permissionRepository.findByResourceTypeAndResourceId(RESOURCE_TYPE_SEGMENT, segmentId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── Private helpers ──

    private void validateGrantRequest(GrantPermissionRequest request) {
        if (request.getUserId() == null && request.getTeamId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "用户ID和团队ID不能同时为空", HttpStatus.BAD_REQUEST);
        }
    }

    private void verifySegmentExists(Long segmentId) {
        if (!segmentRepository.existsById(segmentId)) {
            throw new ResourceNotFoundException(ErrorCode.SEGMENT_NOT_FOUND, "段落不存在");
        }
    }

    private PermissionDTO toDTO(Permission permission) {
        return new PermissionDTO(
                permission.getId(),
                permission.getTemplateId(),
                permission.getUserId(),
                permission.getTeamId(),
                permission.getPermissionType(),
                permission.getGrantedAt(),
                permission.getGrantedBy()
        );
    }
}
