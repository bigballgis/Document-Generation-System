package com.docgen.entity;

/**
 * Enumeration of template sharing scopes in the template market.
 */
public enum ShareScope {
    /** Visible only within the same tenant. */
    TENANT_INTERNAL,
    /** Visible to all tenants globally. */
    GLOBAL
}
