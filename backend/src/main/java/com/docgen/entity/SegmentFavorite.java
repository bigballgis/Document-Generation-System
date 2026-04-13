package com.docgen.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code segment_favorites} table.
 * Tracks which users have favorited which segments.
 * Unique constraint on (segment_id, user_id) prevents duplicate favorites.
 */
@Entity
@Table(name = "segment_favorites",
        uniqueConstraints = @UniqueConstraint(columnNames = {"segment_id", "user_id"}))
public class SegmentFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "segment_id", nullable = false)
    private Long segmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters and Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
