package com.docgen.service;

import com.docgen.dto.CreateTenantRequest;
import com.docgen.dto.TenantDTO;
import com.docgen.dto.TenantUsageDTO;
import com.docgen.dto.UpdateTenantRequest;
import com.docgen.entity.Tenant;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling tenant CRUD, enable/disable, and usage queries.
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantDTO createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Tenant name already exists", HttpStatus.CONFLICT);
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setContactName(request.getContactName());
        tenant.setContactEmail(request.getContactEmail());

        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant created: name={}, id={}", saved.getName(), saved.getId());
        return toDTO(saved);
    }

    @Transactional
    public TenantDTO updateTenant(Long id, UpdateTenantRequest request) {
        Tenant tenant = findTenantOrThrow(id);

        if (request.getName() != null && !request.getName().equals(tenant.getName())) {
            if (tenantRepository.existsByName(request.getName())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Tenant name already exists", HttpStatus.CONFLICT);
            }
            tenant.setName(request.getName());
        }
        if (request.getContactName() != null) {
            tenant.setContactName(request.getContactName());
        }
        if (request.getContactEmail() != null) {
            tenant.setContactEmail(request.getContactEmail());
        }

        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant updated: id={}", saved.getId());
        return toDTO(saved);
    }

    @Transactional
    public TenantDTO enableTenant(Long id) {
        Tenant tenant = findTenantOrThrow(id);
        tenant.setStatus("ACTIVE");
        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant enabled: id={}", id);
        return toDTO(saved);
    }

    @Transactional
    public TenantDTO disableTenant(Long id) {
        Tenant tenant = findTenantOrThrow(id);
        tenant.setStatus("DISABLED");
        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant disabled: id={}", id);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public TenantDTO getTenantById(Long id) {
        return toDTO(findTenantOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<TenantDTO> listTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public TenantUsageDTO getTenantUsage(Long id) {
        Tenant tenant = findTenantOrThrow(id);
        // Placeholder counts — real implementation will query templates, API logs, storage
        return new TenantUsageDTO(
                0L,  // currentTemplateCount
                0L,  // currentMonthApiCalls
                0L,  // usedStorageBytes
                tenant.getMaxTemplates(),
                tenant.getMaxApiCallsMonthly(),
                tenant.getMaxStorageBytes()
        );
    }


    private Tenant findTenantOrThrow(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
    }

    private TenantDTO toDTO(Tenant tenant) {
        return new TenantDTO(
                tenant.getId(),
                tenant.getName(),
                tenant.getContactName(),
                tenant.getContactEmail(),
                tenant.getStatus(),
                tenant.getMaxTemplates(),
                tenant.getMaxApiCallsMonthly(),
                tenant.getMaxStorageBytes(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
