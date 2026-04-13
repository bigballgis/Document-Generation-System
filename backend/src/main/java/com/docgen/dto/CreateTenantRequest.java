package com.docgen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new tenant.
 */
public class CreateTenantRequest {

    @NotBlank(message = "租户名称不能为空")
    private String name;

    private String contactName;

    @Email(message = "联系邮箱格式不正确")
    private String contactEmail;

    public CreateTenantRequest() {}

    public CreateTenantRequest(String name, String contactName, String contactEmail) {
        this.name = name;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
}
