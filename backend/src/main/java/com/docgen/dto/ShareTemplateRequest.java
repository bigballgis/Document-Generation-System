package com.docgen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class ShareTemplateRequest {

    @NotNull(message = "分享范围不能为空")
    @Pattern(regexp = "TENANT_INTERNAL|GLOBAL", message = "分享范围必须为 TENANT_INTERNAL 或 GLOBAL")
    private String shareScope;

    public ShareTemplateRequest() {}

    public ShareTemplateRequest(String shareScope) {
        this.shareScope = shareScope;
    }

    public String getShareScope() { return shareScope; }
    public void setShareScope(String shareScope) { this.shareScope = shareScope; }
}
