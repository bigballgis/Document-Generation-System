package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for segment test data information.
 */
public class SegmentTestDataDTO {

    private Long id;
    private Long segmentId;
    private String name;
    private String testDataJson;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public SegmentTestDataDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTestDataJson() { return testDataJson; }
    public void setTestDataJson(String testDataJson) { this.testDataJson = testDataJson; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
