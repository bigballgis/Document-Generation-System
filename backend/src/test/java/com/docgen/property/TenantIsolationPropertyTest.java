package com.docgen.property;

import com.docgen.dto.UserPrincipal;
import com.docgen.filter.TenantIsolationFilter;
import com.docgen.util.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;

/**
 * Property-based tests for tenant data isolation.
 *
 * <p><b>Validates: Requirements 19.1, 19.3, 49.4</b></p>
 */
@Tag("feature-low-code-document-generation-system-property-17")
class TenantIsolationPropertyTest {

    /**
     * Property 1: Thread isolation guarantee.
     * For any two different tenant IDs set on different threads,
     * each thread should only see its own tenant ID.
     */
    @Property(tries = 100)
    void threadIsolation_eachThreadSeesOnlyOwnTenantId(
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long tenantIdA,
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long tenantIdB
    ) throws InterruptedException {
        Assume.that(tenantIdA != tenantIdB);

        AtomicLong observedByThreadA = new AtomicLong(-1);
        AtomicLong observedByThreadB = new AtomicLong(-1);
        AtomicReference<Long> crossCheckA = new AtomicReference<>();
        AtomicReference<Long> crossCheckB = new AtomicReference<>();

        CountDownLatch bothSet = new CountDownLatch(2);
        CountDownLatch bothRead = new CountDownLatch(2);

        Thread threadA = new Thread(() -> {
            try {
                TenantContext.setCurrentTenantId(tenantIdA);
                bothSet.countDown();
                bothSet.await();
                // Both threads have set their values; read own value
                observedByThreadA.set(TenantContext.getCurrentTenantId());
                bothRead.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                crossCheckA.set(TenantContext.getCurrentTenantId());
                TenantContext.clear();
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                TenantContext.setCurrentTenantId(tenantIdB);
                bothSet.countDown();
                bothSet.await();
                observedByThreadB.set(TenantContext.getCurrentTenantId());
                bothRead.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                crossCheckB.set(TenantContext.getCurrentTenantId());
                TenantContext.clear();
            }
        });

        threadA.start();
        threadB.start();
        threadA.join(5000);
        threadB.join(5000);

        assert observedByThreadA.get() == tenantIdA
                : "Thread A saw " + observedByThreadA.get() + " instead of " + tenantIdA;
        assert observedByThreadB.get() == tenantIdB
                : "Thread B saw " + observedByThreadB.get() + " instead of " + tenantIdB;
    }

    /**
     * Property 2: Cleanup guarantee.
     * For any tenant ID, after TenantContext.clear(), getCurrentTenantId() returns null.
     */
    @Property(tries = 100)
    void clearAlwaysResetsToNull(
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long tenantId
    ) {
        TenantContext.setCurrentTenantId(tenantId);
        assert TenantContext.getCurrentTenantId() != null
                : "Tenant ID should be set before clear";

        TenantContext.clear();

        assert TenantContext.getCurrentTenantId() == null
                : "Tenant ID should be null after clear, but was " + TenantContext.getCurrentTenantId();
    }

    /**
     * Property 3: TenantIsolationFilter sets the exact tenantId from UserPrincipal.
     * For any UserPrincipal with a given tenantId, the filter should set that exact
     * tenantId in TenantContext during filter chain execution, and clear it afterwards.
     */
    @Property(tries = 100)
    void filterSetsExactTenantIdFromPrincipal(
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long userId,
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long tenantId,
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long teamId
    ) throws ServletException, IOException {
        TenantIsolationFilter filter = new TenantIsolationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserPrincipal principal = new UserPrincipal(userId, tenantId, "USER", teamId, "user-" + userId);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<Long> capturedTenantId = new AtomicReference<>();
        FilterChain filterChain = mock(FilterChain.class);
        doAnswer(invocation -> {
            capturedTenantId.set(TenantContext.getCurrentTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        try {
            filter.doFilter(request, response, filterChain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assert capturedTenantId.get() != null
                : "TenantContext should have been set during filter chain execution";
        assert capturedTenantId.get().equals(tenantId)
                : "Expected tenantId " + tenantId + " but got " + capturedTenantId.get();
        assert TenantContext.getCurrentTenantId() == null
                : "TenantContext should be cleared after filter completes";
    }
}
