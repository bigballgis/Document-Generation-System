package com.docgen.dto;

import java.time.Instant;

/**
 * Response DTO for webhook log entries.
 */
public class WebhookLogDTO {

    private Long id;
    private Long webhookConfigId;
    private String eventType;
    private String payload;
    private Integer responseStatus;
    private String responseBody;
    private Instant sentAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWebhookConfigId() { return webhookConfigId; }
    public void setWebhookConfigId(Long webhookConfigId) { this.webhookConfigId = webhookConfigId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
