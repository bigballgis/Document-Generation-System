package com.docgen.repository;

import com.docgen.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Component that enables the Hibernate {@code tenantFilter} on the current
 * {@link Session}, using the tenant ID stored in {@link TenantContext}.
 * <p>
 * Called by {@link com.docgen.config.HibernateFilterConfig} on every
 * API request so that all JPA queries are automatically scoped to the
 * current tenant.
 */
@Component
public class TenantAwareEntityManager {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Enables the Hibernate tenant filter if a tenant ID is present in
     * the current thread context.
     */
    public void enableTenantFilter() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);
        }
    }
}
