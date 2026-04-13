package com.docgen.service;

import com.docgen.dto.AuditLogDTO;
import com.docgen.dto.AuditLogQuery;
import com.docgen.dto.ExportFormat;
import com.docgen.entity.AuditLog;
import com.docgen.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for recording and querying immutable audit log entries.
 * <p>
 * Audit records are stored in an independent table and must not be modified
 * or deleted by business operations. Only the scheduled retention cleanup
 * removes records older than the configured retention period.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final long retentionDays;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper,
                           @Value("${audit.retention-days:365}") long retentionDays) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.retentionDays = retentionDays;
    }

    /**
     * Record an audit event. This is the primary entry point used by AOP aspects
     * or application event listeners to persist audit records.
     */
    @Transactional
    public void log(Long tenantId, Long userId, String action,
                    String resourceType, Long resourceId,
                    String detailsJson, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setTenantId(tenantId);
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setDetailsJson(detailsJson);
        entry.setIpAddress(ipAddress);
        auditLogRepository.save(entry);
        log.debug("Audit log recorded: action={}, resourceType={}, resourceId={}, userId={}",
                action, resourceType, resourceId, userId);
    }

    /**
     * Query audit logs with optional filters (action, userId, time range).
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> queryLogs(Long tenantId, AuditLogQuery query, Pageable pageable) {
        return auditLogRepository.findByFilters(
                tenantId,
                query.getAction(),
                query.getUserId(),
                query.getStartTime(),
                query.getEndTime(),
                pageable
        ).map(this::toDTO);
    }

    /**
     * Export audit logs matching the given filters as CSV or JSON bytes.
     */
    @Transactional(readOnly = true)
    public byte[] exportLogs(Long tenantId, AuditLogQuery query, ExportFormat format) {
        List<AuditLog> logs = auditLogRepository.findAllByFilters(
                tenantId,
                query.getAction(),
                query.getUserId(),
                query.getStartTime(),
                query.getEndTime());

        if (format == ExportFormat.CSV) {
            return toCsv(logs);
        }
        return toJson(logs);
    }

    /**
     * Scheduled cleanup of audit logs older than the configured retention period.
     * Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredLogs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Audit log cleanup: deleted {} records older than {} days", deleted, retentionDays);
        }
    }

    /**
     * Returns the configured retention period in days.
     */
    public long getRetentionDays() {
        return retentionDays;
    }

    // ── Private helpers ──

    private AuditLogDTO toDTO(AuditLog entity) {
        return new AuditLogDTO(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getDetailsJson(),
                entity.getIpAddress(),
                entity.getCreatedAt()
        );
    }

    private byte[] toCsv(List<AuditLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,tenant_id,user_id,action,resource_type,resource_id,details,ip_address,created_at\n");
        for (AuditLog entry : logs) {
            sb.append(entry.getId()).append(',');
            sb.append(entry.getTenantId()).append(',');
            sb.append(entry.getUserId() != null ? entry.getUserId() : "").append(',');
            sb.append(escapeCsv(entry.getAction())).append(',');
            sb.append(escapeCsv(entry.getResourceType())).append(',');
            sb.append(entry.getResourceId() != null ? entry.getResourceId() : "").append(',');
            sb.append(escapeCsv(entry.getDetailsJson())).append(',');
            sb.append(escapeCsv(entry.getIpAddress())).append(',');
            sb.append(entry.getCreatedAt()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] toJson(List<AuditLog> logs) {
        try {
            List<AuditLogDTO> dtos = logs.stream().map(this::toDTO).toList();
            return objectMapper.writeValueAsBytes(dtos);
        } catch (Exception e) {
            log.error("Failed to serialize audit logs to JSON", e);
            return "[]".getBytes(StandardCharsets.UTF_8);
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
