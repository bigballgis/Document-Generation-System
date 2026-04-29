package com.docgen.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class MarketTemplateDTO {

    private Long id;
    private Long templateId;
    private String name;
    private String description;
    private String category;
    private String shareScope;
    private Long sharedBy;
    private int usageCount;
    private BigDecimal rating;
    private Instant createdAt;

    public MarketTemplateDTO() {}

    public MarketTemplateDTO(Long id, Long templateId, String name, String description,
                             String category, String shareScope, Long sharedBy,
                             int usageCount, BigDecimal rating, Instant createdAt) {
        this.id = id;
        this.templateId = templateId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.shareScope = shareScope;
        this.sharedBy = sharedBy;
        this.usageCount = usageCount;
        this.rating = rating;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getShareScope() { return shareScope; }
    public void setShareScope(String shareScope) { this.shareScope = shareScope; }

    public Long getSharedBy() { return sharedBy; }
    public void setSharedBy(Long sharedBy) { this.sharedBy = sharedBy; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
