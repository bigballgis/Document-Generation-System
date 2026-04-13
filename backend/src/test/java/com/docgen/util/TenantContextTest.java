package com.docgen.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_returnsSameValue() {
        TenantContext.setCurrentTenantId(42L);
        assertEquals(42L, TenantContext.getCurrentTenantId());
    }

    @Test
    void get_returnsNullWhenNotSet() {
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void clear_removesStoredValue() {
        TenantContext.setCurrentTenantId(7L);
        TenantContext.clear();
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void set_overwritesPreviousValue() {
        TenantContext.setCurrentTenantId(1L);
        TenantContext.setCurrentTenantId(2L);
        assertEquals(2L, TenantContext.getCurrentTenantId());
    }

    @Test
    void threadIsolation_eachThreadHasOwnValue() throws InterruptedException {
        TenantContext.setCurrentTenantId(100L);

        Thread other = new Thread(() -> {
            assertNull(TenantContext.getCurrentTenantId());
            TenantContext.setCurrentTenantId(200L);
            assertEquals(200L, TenantContext.getCurrentTenantId());
            TenantContext.clear();
        });
        other.start();
        other.join();

        // Main thread value is unaffected
        assertEquals(100L, TenantContext.getCurrentTenantId());
    }
}
