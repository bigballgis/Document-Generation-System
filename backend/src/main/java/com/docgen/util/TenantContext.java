package com.docgen.util;

/**
 * ThreadLocal storage for the current tenant ID.
 * <p>
 * Set by {@code TenantIsolationFilter} at the start of each request and
 * cleared in its {@code finally} block so the value never leaks across
 * requests (important when using thread pools).
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // utility class
    }

    public static void setCurrentTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
