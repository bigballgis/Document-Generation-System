package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request DTO for creating a new API Key.
 */
public class CreateApiKeyRequest {

    @NotBlank(message = "API Key name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private Integer rateLimitPerSecond;
    private Integer rateLimitPerMinute;
    private Integer rateLimitPerHour;
    private Instant expiresAt;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getRateLimitPerSecond() { return rateLimitPerSecond; }
    public void setRateLimitPerSecond(Integer rateLimitPerSecond) { this.rateLimitPerSecond = rateLimitPerSecond; }

    public Integer getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(Integer rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public Integer getRateLimitPerHour() { return rateLimitPerHour; }
    public void setRateLimitPerHour(Integer rateLimitPerHour) { this.rateLimitPerHour = rateLimitPerHour; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
