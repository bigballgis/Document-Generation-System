package com.docgen.controller;

import com.docgen.dto.AuditLogDTO;
import com.docgen.dto.AuditLogQuery;
import com.docgen.dto.ExportFormat;
import com.docgen.dto.UserPrincipal;
import com.docgen.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Audit logs are read-only; no create/update/delete endpoints are exposed.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> queryLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            Pageable pageable) {
        AuditLogQuery query = new AuditLogQuery();
        query.setAction(action);
        query.setUserId(userId);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        Page<AuditLogDTO> page = auditLogService.queryLogs(
                principal.getTenantId(), query, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            @RequestParam(defaultValue = "JSON") ExportFormat format) {
        AuditLogQuery query = new AuditLogQuery();
        query.setAction(action);
        query.setUserId(userId);
        query.setStartTime(startTime);
        query.setEndTime(endTime);

        byte[] data = auditLogService.exportLogs(principal.getTenantId(), query, format);

        HttpHeaders headers = new HttpHeaders();
        if (format == ExportFormat.CSV) {
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.csv");
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.json");
        }
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
