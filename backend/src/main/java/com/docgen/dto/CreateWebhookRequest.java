package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new webhook configuration.
 */
public class CreateWebhookRequest {

    @NotBlank(message = "Webhook URL is required")
    private String url;

    @NotBlank(message = "Webhook secret is required")
    private String secret;

    private String payloadTemplate;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getPayloadTemplate() { return payloadTemplate; }
    public void setPayloadTemplate(String payloadTemplate) { this.payloadTemplate = payloadTemplate; }
}
