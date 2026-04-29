package com.docgen.service;

import com.docgen.dto.GrantPermissionRequest;
import com.docgen.dto.PermissionDTO;
import com.docgen.entity.Permission;
import com.docgen.entity.PermissionType;
import com.docgen.entity.Template;
import com.docgen.exception.AccessDeniedException;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.PermissionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service handling template-level permission management.
 * Permissions are scoped within the current tenant.
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final PermissionRepository permissionRepository;
    private final TemplateRepository templateRepository;

    public PermissionService(PermissionRepository permissionRepository,
                             TemplateRepository templateRepository) {
        this.permissionRepository = permissionRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Check whether a user has a specific permission on a template.
     * Checks both direct user permissions and team-level permissions.
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, Long teamId, Long templateId, PermissionType type) {
        // Check direct user permission
        if (permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(templateId, userId, type)) {
            return true;
        }
        // Check team-level permission
        if (teamId != null) {
            return permissionRepository.existsByTemplateIdAndTeamIdAndPermissionType(templateId, teamId, type);
        }
        return false;
    }

    /**
     * Verify that a user has the required permission on a template.
     * Throws AccessDeniedException (403) if the user lacks the permission.
     * Template owner and TENANT_ADMIN/SUPER_ADMIN bypass permission checks.
     */
    @Transactional(readOnly = true)
    public void checkPermission(Long userId, Long teamId, String role, Long templateId, PermissionType type) {
        // SUPER_ADMIN and TENANT_ADMIN bypass permission checks
        if ("SUPER_ADMIN".equals(role) || "TENANT_ADMIN".equals(role)) {
            return;
        }

        // Template owner has all permissions
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        // Verify template belongs to current tenant
        Long currentTenantId = TenantContext.getCurrentTenantId();
        if (currentTenantId != null && !currentTenantId.equals(template.getTenantId())) {
            throw new AccessDeniedException("无权访问其他租户的模板");
        }

        if (userId.equals(template.getCreatedBy())) {
            return;
        }

        if (!hasPermission(userId, teamId, templateId, type)) {
            throw new AccessDeniedException("无权执行此操作，需要 " + type + " 权限");
        }
    }

    /**
     * Grant a permission on a template.
     */
    @Transactional
    public PermissionDTO grantPermission(Long templateId, GrantPermissionRequest request, Long grantedBy) {
        validateGrantRequest(request);
        verifyTemplateExists(templateId);

        // Check for duplicate
        if (request.getUserId() != null &&
                permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                        templateId, request.getUserId(), request.getPermissionType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "该用户已拥有此权限", HttpStatus.CONFLICT);
        }
        if (request.getTeamId() != null &&
                permissionRepository.existsByTemplateIdAndTeamIdAndPermissionType(
                        templateId, request.getTeamId(), request.getPermissionType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "该团队已拥有此权限", HttpStatus.CONFLICT);
        }

        Permission permission = new Permission();
        permission.setTemplateId(templateId);
        permission.setUserId(request.getUserId());
        permission.setTeamId(request.getTeamId());
        permission.setPermissionType(request.getPermissionType());
        permission.setGrantedBy(grantedBy);

        Permission saved = permissionRepository.save(permission);
        log.info("Permission granted: templateId={}, userId={}, teamId={}, type={}",
                templateId, request.getUserId(), request.getTeamId(), request.getPermissionType());
        return toDTO(saved);
    }

    /**
     * Revoke a specific permission by its ID.
     */
    @Transactional
    public void revokePermission(Long templateId, Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_FAILED, "权限记录不存在"));

        if (!permission.getTemplateId().equals(templateId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "权限记录不属于该模板", HttpStatus.BAD_REQUEST);
        }

        permissionRepository.delete(permission);
        log.info("Permission revoked: id={}, templateId={}", permissionId, templateId);
    }

    /**
     * List all permissions for a template.
     */
    @Transactional(readOnly = true)
    public List<PermissionDTO> getTemplatePermissions(Long templateId) {
        verifyTemplateExists(templateId);
        return permissionRepository.findByTemplateId(templateId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * List all permissions for a user.
     */
    @Transactional(readOnly = true)
    public List<PermissionDTO> getUserPermissions(Long userId) {
        return permissionRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    private void validateGrantRequest(GrantPermissionRequest request) {
        if (request.getUserId() == null && request.getTeamId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "用户ID和团队ID不能同时为空", HttpStatus.BAD_REQUEST);
        }
    }

    private void verifyTemplateExists(Long templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw new ResourceNotFoundException(ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在");
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
