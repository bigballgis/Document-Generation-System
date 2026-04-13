package com.docgen.dto;

import java.time.Instant;

/**
 * Query parameters for filtering audit log records.
 */
public class AuditLogQuery {

    private String action;
    private Long userId;
    private Instant startTime;
    private Instant endTime;

    public AuditLogQuery() {}

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
}
