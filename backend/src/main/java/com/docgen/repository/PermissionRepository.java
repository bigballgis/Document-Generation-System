package com.docgen.repository;

import com.docgen.entity.Permission;
import com.docgen.entity.PermissionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Permission} entities.
 */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findByTemplateId(Long templateId);

    List<Permission> findByUserId(Long userId);

    List<Permission> findByTeamId(Long teamId);

    boolean existsByTemplateIdAndUserIdAndPermissionType(Long templateId, Long userId, PermissionType permissionType);

    boolean existsByTemplateIdAndTeamIdAndPermissionType(Long templateId, Long teamId, PermissionType permissionType);

    List<Permission> findByTemplateIdAndUserId(Long templateId, Long userId);

    List<Permission> findByTemplateIdAndTeamId(Long templateId, Long teamId);

    void deleteByTemplateId(Long templateId);

    // ── Resource-based queries (for Segment permissions) ──

    List<Permission> findByResourceTypeAndResourceId(String resourceType, Long resourceId);

    boolean existsByResourceTypeAndResourceIdAndUserIdAndPermissionType(
            String resourceType, Long resourceId, Long userId, PermissionType permissionType);

    boolean existsByResourceTypeAndResourceIdAndTeamIdAndPermissionType(
            String resourceType, Long resourceId, Long teamId, PermissionType permissionType);
}
