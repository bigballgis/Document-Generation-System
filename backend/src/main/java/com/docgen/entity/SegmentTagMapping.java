package com.docgen.entity;

import jakarta.persistence.*;

/**
 * JPA Entity mapping to the {@code segment_tag_mappings} table.
 * Associates a segment with a tag from the shared {@code template_tags} table.
 * Uses a surrogate primary key (id) with a unique constraint on (segment_id, tag_id).
 */
@Entity
@Table(name = "segment_tag_mappings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"segment_id", "tag_id"}))
public class SegmentTagMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "segment_id", nullable = false)
    private Long segmentId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    public SegmentTagMapping() {}

    public SegmentTagMapping(Long segmentId, Long tagId) {
        this.segmentId = segmentId;
        this.tagId = tagId;
    }

    // ── Getters and Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
}
