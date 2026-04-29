package com.docgen.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code teams} table.
 */
@Entity
@Table(name = "teams", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", nullable = false, length = 32)
    private TeamApprovalMode approvalMode = TeamApprovalMode.CROSS_REVIEW;

    /** Azure AD group object id for membership sync (cross-review mode). */
    @Column(name = "ad_group_object_id", length = 128)
    private String adGroupObjectId;

    /** Azure AD group object id for makers (maker-checker mode). */
    @Column(name = "ad_maker_group_object_id", length = 128)
    private String adMakerGroupObjectId;

    /** Azure AD group object id for checkers (maker-checker mode). */
    @Column(name = "ad_checker_group_object_id", length = 128)
    private String adCheckerGroupObjectId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TeamApprovalMode getApprovalMode() { return approvalMode; }
    public void setApprovalMode(TeamApprovalMode approvalMode) { this.approvalMode = approvalMode; }

    public String getAdGroupObjectId() { return adGroupObjectId; }
    public void setAdGroupObjectId(String adGroupObjectId) { this.adGroupObjectId = adGroupObjectId; }

    public String getAdMakerGroupObjectId() { return adMakerGroupObjectId; }
    public void setAdMakerGroupObjectId(String adMakerGroupObjectId) { this.adMakerGroupObjectId = adMakerGroupObjectId; }

    public String getAdCheckerGroupObjectId() { return adCheckerGroupObjectId; }
    public void setAdCheckerGroupObjectId(String adCheckerGroupObjectId) { this.adCheckerGroupObjectId = adCheckerGroupObjectId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

