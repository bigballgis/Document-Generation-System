package com.docgen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateLimitStatusDTO {

    private Long apiKeyId;
    private String apiKeyName;
    private int limitPerSecond;
    private int limitPerMinute;
    private int limitPerHour;
    private long remainingPerSecond;
    private long remainingPerMinute;
    private long remainingPerHour;


    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }

    public String getApiKeyName() { return apiKeyName; }
    public void setApiKeyName(String apiKeyName) { this.apiKeyName = apiKeyName; }

    public int getLimitPerSecond() { return limitPerSecond; }
    public void setLimitPerSecond(int limitPerSecond) { this.limitPerSecond = limitPerSecond; }

    public int getLimitPerMinute() { return limitPerMinute; }
    public void setLimitPerMinute(int limitPerMinute) { this.limitPerMinute = limitPerMinute; }

    public int getLimitPerHour() { return limitPerHour; }
    public void setLimitPerHour(int limitPerHour) { this.limitPerHour = limitPerHour; }

    public long getRemainingPerSecond() { return remainingPerSecond; }
    public void setRemainingPerSecond(long remainingPerSecond) { this.remainingPerSecond = remainingPerSecond; }

    public long getRemainingPerMinute() { return remainingPerMinute; }
    public void setRemainingPerMinute(long remainingPerMinute) { this.remainingPerMinute = remainingPerMinute; }

    public long getRemainingPerHour() { return remainingPerHour; }
    public void setRemainingPerHour(long remainingPerHour) { this.remainingPerHour = remainingPerHour; }
}

