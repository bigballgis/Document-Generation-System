package com.docgen.service;

import com.docgen.dto.AuditLogDTO;
import com.docgen.dto.AuditLogQuery;
import com.docgen.dto.ExportFormat;
import com.docgen.entity.AuditLog;
import com.docgen.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        service = new AuditLogService(auditLogRepository, objectMapper, 365);
    }

    // ── log ──

    @Test
    void log_savesAuditEntry() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        service.log(10L, 42L, "CREATE_TEMPLATE", "TEMPLATE", 100L,
                "{\"name\":\"test\"}", "192.168.1.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals(10L, saved.getTenantId());
        assertEquals(42L, saved.getUserId());
        assertEquals("CREATE_TEMPLATE", saved.getAction());
        assertEquals("TEMPLATE", saved.getResourceType());
        assertEquals(100L, saved.getResourceId());
        assertEquals("{\"name\":\"test\"}", saved.getDetailsJson());
        assertEquals("192.168.1.1", saved.getIpAddress());
    }

    @Test
    void log_withNullOptionalFields() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog a = inv.getArgument(0);
            a.setId(2L);
            return a;
        });

        service.log(10L, null, "LOGIN", null, null, null, "10.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals(10L, saved.getTenantId());
        assertNull(saved.getUserId());
        assertEquals("LOGIN", saved.getAction());
        assertNull(saved.getResourceType());
        assertNull(saved.getResourceId());
        assertNull(saved.getDetailsJson());
        assertEquals("10.0.0.1", saved.getIpAddress());
    }

    // ── queryLogs ──

    @Test
    void queryLogs_delegatesToRepository() {
        AuditLog entry = createSampleAuditLog(1L, 10L);
        Page<AuditLog> page = new PageImpl<>(List.of(entry));
        Pageable pageable = PageRequest.of(0, 20);

        when(auditLogRepository.findByFilters(eq(10L), eq("CREATE_TEMPLATE"),
                eq(42L), any(), any(), eq(pageable)))
                .thenReturn(page);

        AuditLogQuery query = new AuditLogQuery();
        query.setAction("CREATE_TEMPLATE");
        query.setUserId(42L);

        Page<AuditLogDTO> result = service.queryLogs(10L, query, pageable);

        assertEquals(1, result.getTotalElements());
        AuditLogDTO dto = result.getContent().get(0);
        assertEquals(1L, dto.getId());
        assertEquals("CREATE_TEMPLATE", dto.getAction());
        assertEquals(42L, dto.getUserId());
    }

    @Test
    void queryLogs_emptyFilters() {
        Page<AuditLog> emptyPage = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 20);

        when(auditLogRepository.findByFilters(eq(10L), isNull(), isNull(),
                isNull(), isNull(), eq(pageable)))
                .thenReturn(emptyPage);

        AuditLogQuery query = new AuditLogQuery();
        Page<AuditLogDTO> result = service.queryLogs(10L, query, pageable);

        assertEquals(0, result.getTotalElements());
    }

    // ── exportLogs CSV ──

    @Test
    void exportLogs_csv_returnsValidCsv() {
        AuditLog entry = createSampleAuditLog(1L, 10L);
        when(auditLogRepository.findAllByFilters(eq(10L), isNull(), isNull(),
                isNull(), isNull()))
                .thenReturn(List.of(entry));

        AuditLogQuery query = new AuditLogQuery();
        byte[] csv = service.exportLogs(10L, query, ExportFormat.CSV);

        String csvStr = new String(csv);
        assertTrue(csvStr.startsWith("id,tenant_id,user_id,action,resource_type,resource_id,details,ip_address,created_at"));
        assertTrue(csvStr.contains("CREATE_TEMPLATE"));
        assertTrue(csvStr.contains("TEMPLATE"));
    }

    @Test
    void exportLogs_csv_escapeCommasInDetails() {
        AuditLog entry = createSampleAuditLog(1L, 10L);
        entry.setDetailsJson("{\"key\":\"value,with,commas\"}");
        when(auditLogRepository.findAllByFilters(eq(10L), isNull(), isNull(),
                isNull(), isNull()))
                .thenReturn(List.of(entry));

        AuditLogQuery query = new AuditLogQuery();
        byte[] csv = service.exportLogs(10L, query, ExportFormat.CSV);

        String csvStr = new String(csv);
        // Details with commas should be quoted
        assertTrue(csvStr.contains("\""));
    }

    // ── exportLogs JSON ──

    @Test
    void exportLogs_json_returnsValidJson() {
        AuditLog entry = createSampleAuditLog(1L, 10L);
        when(auditLogRepository.findAllByFilters(eq(10L), isNull(), isNull(),
                isNull(), isNull()))
                .thenReturn(List.of(entry));

        AuditLogQuery query = new AuditLogQuery();
        byte[] json = service.exportLogs(10L, query, ExportFormat.JSON);

        String jsonStr = new String(json);
        assertTrue(jsonStr.startsWith("["));
        assertTrue(jsonStr.contains("CREATE_TEMPLATE"));
    }

    @Test
    void exportLogs_json_emptyList() {
        when(auditLogRepository.findAllByFilters(eq(10L), isNull(), isNull(),
                isNull(), isNull()))
                .thenReturn(List.of());

        AuditLogQuery query = new AuditLogQuery();
        byte[] json = service.exportLogs(10L, query, ExportFormat.JSON);

        assertEquals("[]", new String(json));
    }

    // ── cleanupExpiredLogs ──

    @Test
    void cleanupExpiredLogs_deletesOldRecords() {
        when(auditLogRepository.deleteByCreatedAtBefore(any(Instant.class))).thenReturn(5);

        service.cleanupExpiredLogs();

        verify(auditLogRepository).deleteByCreatedAtBefore(any(Instant.class));
    }

    // ── retentionDays ──

    @Test
    void getRetentionDays_returnsConfiguredValue() {
        assertEquals(365, service.getRetentionDays());
    }

    @Test
    void customRetentionDays() {
        AuditLogService customService = new AuditLogService(auditLogRepository, objectMapper, 90);
        assertEquals(90, customService.getRetentionDays());
    }

    // ── Helpers ──

    private AuditLog createSampleAuditLog(Long id, Long tenantId) {
        AuditLog entry = new AuditLog();
        entry.setId(id);
        entry.setTenantId(tenantId);
        entry.setUserId(42L);
        entry.setAction("CREATE_TEMPLATE");
        entry.setResourceType("TEMPLATE");
        entry.setResourceId(100L);
        entry.setDetailsJson("{\"name\":\"test\"}");
        entry.setIpAddress("192.168.1.1");
        entry.setCreatedAt(Instant.now());
        return entry;
    }
}
