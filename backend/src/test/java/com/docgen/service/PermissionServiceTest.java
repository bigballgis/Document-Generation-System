package com.docgen.service;

import com.docgen.dto.GrantPermissionRequest;
import com.docgen.dto.PermissionDTO;
import com.docgen.entity.Permission;
import com.docgen.entity.PermissionType;
import com.docgen.entity.Template;
import com.docgen.exception.AccessDeniedException;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.PermissionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private TemplateRepository templateRepository;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(permissionRepository, templateRepository);
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── hasPermission tests ──

    @Test
    void hasPermission_directUserPermission_returnsTrue() {
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 1L, PermissionType.VIEW)).thenReturn(true);

        assertTrue(permissionService.hasPermission(1L, null, 10L, PermissionType.VIEW));
    }

    @Test
    void hasPermission_teamPermission_returnsTrue() {
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 1L, PermissionType.EDIT)).thenReturn(false);
        when(permissionRepository.existsByTemplateIdAndTeamIdAndPermissionType(
                10L, 5L, PermissionType.EDIT)).thenReturn(true);

        assertTrue(permissionService.hasPermission(1L, 5L, 10L, PermissionType.EDIT));
    }

    @Test
    void hasPermission_noPermission_returnsFalse() {
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 1L, PermissionType.DELETE)).thenReturn(false);

        assertFalse(permissionService.hasPermission(1L, null, 10L, PermissionType.DELETE));
    }

    @Test
    void hasPermission_noUserPermButNoTeam_returnsFalse() {
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 1L, PermissionType.CALL_API)).thenReturn(false);

        assertFalse(permissionService.hasPermission(1L, null, 10L, PermissionType.CALL_API));
    }

    // ── checkPermission tests ──

    @Test
    void checkPermission_superAdmin_bypasses() {
        // Should not throw
        permissionService.checkPermission(1L, null, "SUPER_ADMIN", 10L, PermissionType.DELETE);
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void checkPermission_tenantAdmin_bypasses() {
        permissionService.checkPermission(1L, null, "TENANT_ADMIN", 10L, PermissionType.DELETE);
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void checkPermission_templateOwner_bypasses() {
        Template template = createTestTemplate(10L, 1L, 1L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        permissionService.checkPermission(1L, null, "USER", 10L, PermissionType.DELETE);
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void checkPermission_withPermission_passes() {
        Template template = createTestTemplate(10L, 1L, 99L); // different owner
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 1L, PermissionType.VIEW)).thenReturn(true);

        assertDoesNotThrow(() ->
                permissionService.checkPermission(1L, null, "USER", 10L, PermissionType.VIEW));
    }

    @Test
    void checkPermission_noPermission_throwsAccessDenied() {
        Template template = createTestTemplate(10L, 1L, 99L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 1L, PermissionType.EDIT)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                permissionService.checkPermission(1L, null, "USER", 10L, PermissionType.EDIT));
    }

    @Test
    void checkPermission_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                permissionService.checkPermission(1L, null, "USER", 99L, PermissionType.VIEW));
    }

    @Test
    void checkPermission_crossTenant_throwsAccessDenied() {
        Template template = createTestTemplate(10L, 2L, 99L); // different tenant
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        assertThrows(AccessDeniedException.class, () ->
                permissionService.checkPermission(1L, null, "USER", 10L, PermissionType.VIEW));
    }

    // ── grantPermission tests ──

    @Test
    void grantPermission_toUser_success() {
        when(templateRepository.existsById(10L)).thenReturn(true);
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 2L, PermissionType.VIEW)).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> {
            Permission p = inv.getArgument(0);
            p.setId(1L);
            p.setGrantedAt(Instant.now());
            return p;
        });

        GrantPermissionRequest request = new GrantPermissionRequest(2L, null, PermissionType.VIEW);
        PermissionDTO result = permissionService.grantPermission(10L, request, 1L);

        assertEquals(10L, result.getTemplateId());
        assertEquals(2L, result.getUserId());
        assertNull(result.getTeamId());
        assertEquals(PermissionType.VIEW, result.getPermissionType());
        assertEquals(1L, result.getGrantedBy());
    }

    @Test
    void grantPermission_toTeam_success() {
        when(templateRepository.existsById(10L)).thenReturn(true);
        when(permissionRepository.existsByTemplateIdAndTeamIdAndPermissionType(
                10L, 5L, PermissionType.EDIT)).thenReturn(false);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> {
            Permission p = inv.getArgument(0);
            p.setId(2L);
            p.setGrantedAt(Instant.now());
            return p;
        });

        GrantPermissionRequest request = new GrantPermissionRequest(null, 5L, PermissionType.EDIT);
        PermissionDTO result = permissionService.grantPermission(10L, request, 1L);

        assertEquals(10L, result.getTemplateId());
        assertNull(result.getUserId());
        assertEquals(5L, result.getTeamId());
        assertEquals(PermissionType.EDIT, result.getPermissionType());
    }

    @Test
    void grantPermission_duplicateUser_throwsConflict() {
        when(templateRepository.existsById(10L)).thenReturn(true);
        when(permissionRepository.existsByTemplateIdAndUserIdAndPermissionType(
                10L, 2L, PermissionType.VIEW)).thenReturn(true);

        GrantPermissionRequest request = new GrantPermissionRequest(2L, null, PermissionType.VIEW);
        assertThrows(BusinessException.class, () ->
                permissionService.grantPermission(10L, request, 1L));
    }

    @Test
    void grantPermission_duplicateTeam_throwsConflict() {
        when(templateRepository.existsById(10L)).thenReturn(true);
        when(permissionRepository.existsByTemplateIdAndTeamIdAndPermissionType(
                10L, 5L, PermissionType.EDIT)).thenReturn(true);

        GrantPermissionRequest request = new GrantPermissionRequest(null, 5L, PermissionType.EDIT);
        assertThrows(BusinessException.class, () ->
                permissionService.grantPermission(10L, request, 1L));
    }

    @Test
    void grantPermission_bothNull_throwsValidation() {
        GrantPermissionRequest request = new GrantPermissionRequest(null, null, PermissionType.VIEW);
        assertThrows(BusinessException.class, () ->
                permissionService.grantPermission(10L, request, 1L));
    }

    @Test
    void grantPermission_templateNotFound_throws() {
        when(templateRepository.existsById(99L)).thenReturn(false);

        GrantPermissionRequest request = new GrantPermissionRequest(2L, null, PermissionType.VIEW);
        assertThrows(ResourceNotFoundException.class, () ->
                permissionService.grantPermission(99L, request, 1L));
    }

    // ── revokePermission tests ──

    @Test
    void revokePermission_success() {
        Permission permission = createTestPermission(1L, 10L, 2L, null, PermissionType.VIEW);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(permission));

        permissionService.revokePermission(10L, 1L);

        verify(permissionRepository).delete(permission);
    }

    @Test
    void revokePermission_notFound_throws() {
        when(permissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                permissionService.revokePermission(10L, 99L));
    }

    @Test
    void revokePermission_wrongTemplate_throws() {
        Permission permission = createTestPermission(1L, 20L, 2L, null, PermissionType.VIEW);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(permission));

        assertThrows(BusinessException.class, () ->
                permissionService.revokePermission(10L, 1L));
    }

    // ── getTemplatePermissions tests ──

    @Test
    void getTemplatePermissions_returnsList() {
        when(templateRepository.existsById(10L)).thenReturn(true);
        Permission p1 = createTestPermission(1L, 10L, 2L, null, PermissionType.VIEW);
        Permission p2 = createTestPermission(2L, 10L, null, 5L, PermissionType.EDIT);
        when(permissionRepository.findByTemplateId(10L)).thenReturn(List.of(p1, p2));

        List<PermissionDTO> result = permissionService.getTemplatePermissions(10L);

        assertEquals(2, result.size());
    }

    @Test
    void getTemplatePermissions_templateNotFound_throws() {
        when(templateRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                permissionService.getTemplatePermissions(99L));
    }

    // ── getUserPermissions tests ──

    @Test
    void getUserPermissions_returnsList() {
        Permission p1 = createTestPermission(1L, 10L, 2L, null, PermissionType.VIEW);
        when(permissionRepository.findByUserId(2L)).thenReturn(List.of(p1));

        List<PermissionDTO> result = permissionService.getUserPermissions(2L);

        assertEquals(1, result.size());
        assertEquals(PermissionType.VIEW, result.get(0).getPermissionType());
    }

    // ── Helpers ──

    private Template createTestTemplate(Long id, Long tenantId, Long createdBy) {
        Template template = new Template();
        template.setId(id);
        template.setTenantId(tenantId);
        template.setCreatedBy(createdBy);
        template.setName("Test Template");
        template.setTemplateFilePath("/test/path.docx");
        return template;
    }

    private Permission createTestPermission(Long id, Long templateId, Long userId,
                                             Long teamId, PermissionType type) {
        Permission p = new Permission();
        p.setId(id);
        p.setTemplateId(templateId);
        p.setUserId(userId);
        p.setTeamId(teamId);
        p.setPermissionType(type);
        p.setGrantedAt(Instant.now());
        p.setGrantedBy(1L);
        return p;
    }
}
