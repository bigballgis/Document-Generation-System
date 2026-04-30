package com.docgen.service;

import com.docgen.dto.CreateTenantRequest;
import com.docgen.dto.TenantDTO;
import com.docgen.dto.TenantUsageDTO;
import com.docgen.dto.UpdateTenantRequest;
import com.docgen.entity.Tenant;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository);
    }


    @Test
    void createTenant_success() {
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "John", "john@acme.com");
        when(tenantRepository.existsByName("Acme Corp")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        TenantDTO result = tenantService.createTenant(request);

        assertEquals("Acme Corp", result.getName());
        assertEquals("John", result.getContactName());
        assertEquals("john@acme.com", result.getContactEmail());
        assertEquals("ACTIVE", result.getStatus());
        assertNotNull(result.getId());
    }

    @Test
    void createTenant_duplicateName_throws() {
        CreateTenantRequest request = new CreateTenantRequest("Existing", "Jane", "jane@test.com");
        when(tenantRepository.existsByName("Existing")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantService.createTenant(request));
        assertEquals("Tenant name already exists", ex.getMessage());
    }


    @Test
    void updateTenant_success() {
        Tenant existing = createTestTenant();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tenantRepository.existsByName("New Name")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantRequest request = new UpdateTenantRequest("New Name", "Jane", "jane@acme.com");
        TenantDTO result = tenantService.updateTenant(1L, request);

        assertEquals("New Name", result.getName());
        assertEquals("Jane", result.getContactName());
        assertEquals("jane@acme.com", result.getContactEmail());
    }

    @Test
    void updateTenant_notFound_throws() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tenantService.updateTenant(99L, new UpdateTenantRequest()));
    }


    @Test
    void enableTenant_success() {
        Tenant tenant = createTestTenant();
        tenant.setStatus("DISABLED");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantDTO result = tenantService.enableTenant(1L);

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void disableTenant_success() {
        Tenant tenant = createTestTenant();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantDTO result = tenantService.disableTenant(1L);

        assertEquals("DISABLED", result.getStatus());
    }


    @Test
    void getTenantById_success() {
        Tenant tenant = createTestTenant();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        TenantDTO result = tenantService.getTenantById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Acme Corp", result.getName());
    }

    @Test
    void getTenantById_notFound_throws() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tenantService.getTenantById(99L));
    }

    @Test
    void listTenants_returnsPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Tenant tenant = createTestTenant();
        Page<Tenant> page = new PageImpl<>(List.of(tenant), pageable, 1);
        when(tenantRepository.findAll(pageable)).thenReturn(page);

        Page<TenantDTO> result = tenantService.listTenants(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Acme Corp", result.getContent().get(0).getName());
    }


    @Test
    void getTenantUsage_returnsPlaceholderStats() {
        Tenant tenant = createTestTenant();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        TenantUsageDTO usage = tenantService.getTenantUsage(1L);

        assertEquals(0L, usage.getCurrentTemplateCount());
        assertEquals(0L, usage.getCurrentMonthApiCalls());
        assertEquals(0L, usage.getUsedStorageBytes());
        assertEquals(100, usage.getMaxTemplates());
        assertEquals(100000L, usage.getMaxApiCallsMonthly());
        assertEquals(10737418240L, usage.getMaxStorageBytes());
    }


    private Tenant createTestTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Acme Corp");
        tenant.setContactName("John");
        tenant.setContactEmail("john@acme.com");
        tenant.setStatus("ACTIVE");
        tenant.setMaxTemplates(100);
        tenant.setMaxApiCallsMonthly(100000L);
        tenant.setMaxStorageBytes(10737418240L);
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        return tenant;
    }
}

