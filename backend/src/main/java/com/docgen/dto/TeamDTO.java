package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for team information.
 */
public class TeamDTO {

    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private Instant createdAt;

    public TeamDTO() {}

    public TeamDTO(Long id, Long tenantId, String name, String description, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
