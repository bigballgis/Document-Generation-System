package com.docgen.dto;

/**
 * Request DTO for updating an existing webhook configuration.
 */
public class UpdateWebhookRequest {

    private String url;
    private String secret;
    private String payloadTemplate;
    private Boolean enabled;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getPayloadTemplate() { return payloadTemplate; }
    public void setPayloadTemplate(String payloadTemplate) { this.payloadTemplate = payloadTemplate; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
