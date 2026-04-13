package com.docgen.dto;

import java.time.Instant;

/**
 * DTO representing the edit lock status of a segment.
 */
public class LockInfo {

    private Long segmentId;
    private Long lockedBy;
    private String lockedByUsername;
    private Instant lockedAt;
    private Instant expiresAt;

    public LockInfo() {}

    public LockInfo(Long segmentId, Long lockedBy, String lockedByUsername, Instant lockedAt, Instant expiresAt) {
        this.segmentId = segmentId;
        this.lockedBy = lockedBy;
        this.lockedByUsername = lockedByUsername;
        this.lockedAt = lockedAt;
        this.expiresAt = expiresAt;
    }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Long getLockedBy() { return lockedBy; }
    public void setLockedBy(Long lockedBy) { this.lockedBy = lockedBy; }

    public String getLockedByUsername() { return lockedByUsername; }
    public void setLockedByUsername(String lockedByUsername) { this.lockedByUsername = lockedByUsername; }

    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
