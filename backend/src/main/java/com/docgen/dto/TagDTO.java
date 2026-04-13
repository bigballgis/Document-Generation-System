package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for template tag information.
 */
public class TagDTO {

    private Long id;
    private Long tenantId;
    private String name;
    private Instant createdAt;

    public TagDTO() {}

    public TagDTO(Long id, Long tenantId, String name, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
