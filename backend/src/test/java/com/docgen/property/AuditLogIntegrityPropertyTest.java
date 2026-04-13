package com.docgen.property;

import com.docgen.entity.AuditLog;
import com.docgen.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for audit log integrity.
 *
 * <p>Verifies that every call to {@code AuditLogService.log()} results in exactly
 * one save to the repository, and the saved {@link AuditLog} entity contains all
 * provided fields without loss or corruption.</p>
 *
 * <p><b>Validates: Requirements 37.1-37.4</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 12: 审计日志完整性")
class AuditLogIntegrityPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates an AuditLogService with a mocked repository that captures saved entities.
     */
    private AuditLogServiceTestHarness createHarness() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new com.docgen.service.AuditLogService(repository, objectMapper, 365L);
        return new AuditLogServiceTestHarness(service, repository);
    }

    // ── Property Tests ──

    /**
     * Property 12a: Every call to AuditLogService.log() results in exactly one
     * save to the repository — no more, no less.
     */
    @Property(tries = 100)
    void everyLogCallResultsInExactlyOneSave(
            @ForAll("auditLogInputs") AuditLogInput input
    ) {
        var harness = createHarness();

        harness.service().log(
                input.tenantId(), input.userId(), input.action(),
                input.resourceType(), input.resourceId(),
                input.detailsJson(), input.ipAddress()
        );

        verify(harness.repository(), times(1)).save(any(AuditLog.class));
    }

    /**
     * Property 12b: The saved AuditLog entity contains all provided fields —
     * tenantId, userId, action, resourceType, resourceId, detailsJson, ipAddress.
     * No field is lost or corrupted during the save operation.
     */
    @Property(tries = 100)
    void savedEntityContainsAllProvidedFields(
            @ForAll("auditLogInputs") AuditLogInput input
    ) {
        var harness = createHarness();
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        harness.service().log(
                input.tenantId(), input.userId(), input.action(),
                input.resourceType(), input.resourceId(),
                input.detailsJson(), input.ipAddress()
        );

        verify(harness.repository()).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals(input.tenantId(), saved.getTenantId(), "tenantId must match");
        assertEquals(input.userId(), saved.getUserId(), "userId must match");
        assertEquals(input.action(), saved.getAction(), "action must match");
        assertEquals(input.resourceType(), saved.getResourceType(), "resourceType must match");
        assertEquals(input.resourceId(), saved.getResourceId(), "resourceId must match");
        assertEquals(input.detailsJson(), saved.getDetailsJson(), "detailsJson must match");
        assertEquals(input.ipAddress(), saved.getIpAddress(), "ipAddress must match");
    }

    /**
     * Property 12c: The action field is never null or blank in the saved record,
     * given that the input action is always a valid non-blank string.
     */
    @Property(tries = 100)
    void actionFieldIsNeverNullOrBlankInSavedRecord(
            @ForAll("auditLogInputs") AuditLogInput input
    ) {
        var harness = createHarness();
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        harness.service().log(
                input.tenantId(), input.userId(), input.action(),
                input.resourceType(), input.resourceId(),
                input.detailsJson(), input.ipAddress()
        );

        verify(harness.repository()).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertNotNull(saved.getAction(), "action must not be null");
        assertFalse(saved.getAction().isBlank(), "action must not be blank");
    }

    /**
     * Property 12d: Multiple sequential log calls each produce independent saves
     * with their own distinct data — no cross-contamination between records.
     */
    @Property(tries = 50)
    void multipleLogCallsProduceIndependentSaves(
            @ForAll("auditLogInputs") AuditLogInput input1,
            @ForAll("auditLogInputs") AuditLogInput input2
    ) {
        var harness = createHarness();
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        harness.service().log(
                input1.tenantId(), input1.userId(), input1.action(),
                input1.resourceType(), input1.resourceId(),
                input1.detailsJson(), input1.ipAddress()
        );
        harness.service().log(
                input2.tenantId(), input2.userId(), input2.action(),
                input2.resourceType(), input2.resourceId(),
                input2.detailsJson(), input2.ipAddress()
        );

        verify(harness.repository(), times(2)).save(captor.capture());
        var savedEntities = captor.getAllValues();

        AuditLog first = savedEntities.get(0);
        AuditLog second = savedEntities.get(1);

        assertEquals(input1.action(), first.getAction(), "First save action must match input1");
        assertEquals(input1.tenantId(), first.getTenantId(), "First save tenantId must match input1");
        assertEquals(input2.action(), second.getAction(), "Second save action must match input2");
        assertEquals(input2.tenantId(), second.getTenantId(), "Second save tenantId must match input2");
    }

    // ── Generators ──

    @Provide
    Arbitrary<AuditLogInput> auditLogInputs() {
        Arbitrary<Long> tenantIds = Arbitraries.longs().between(1L, 10_000L);
        Arbitrary<Long> userIds = Arbitraries.longs().between(1L, 10_000L);
        Arbitrary<String> actions = Arbitraries.of(
                "TEMPLATE_CREATE", "TEMPLATE_UPDATE", "TEMPLATE_DELETE",
                "PERMISSION_GRANT", "PERMISSION_REVOKE",
                "API_CALL", "USER_LOGIN", "USER_LOGOUT"
        );
        Arbitrary<String> resourceTypes = Arbitraries.of(
                "TEMPLATE", "PERMISSION", "API_ENDPOINT", "USER", "DATA_SOURCE"
        );
        Arbitrary<Long> resourceIds = Arbitraries.longs().between(1L, 100_000L);
        Arbitrary<String> detailsJsons = Arbitraries.of(
                "{\"name\":\"test\"}", "{\"field\":\"value\",\"count\":42}",
                "{}", null
        );
        Arbitrary<String> ipAddresses = Arbitraries.integers().between(1, 254)
                .list().ofSize(4)
                .map(octets -> octets.get(0) + "." + octets.get(1) + "." + octets.get(2) + "." + octets.get(3));

        return Combinators.combine(tenantIds, userIds, actions, resourceTypes, resourceIds, detailsJsons, ipAddresses)
                .as(AuditLogInput::new);
    }

    // ── Supporting types ──

    record AuditLogInput(Long tenantId, Long userId, String action,
                         String resourceType, Long resourceId,
                         String detailsJson, String ipAddress) {}

    record AuditLogServiceTestHarness(
            com.docgen.service.AuditLogService service,
            AuditLogRepository repository) {}
}
