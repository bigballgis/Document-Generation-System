package com.docgen.dto;

import jakarta.validation.constraints.Email;

public class UpdateTenantRequest {

    private String name;
    private String contactName;

    @Email(message = "联系邮箱格式不正确")
    private String contactEmail;

    public UpdateTenantRequest() {}

    public UpdateTenantRequest(String name, String contactName, String contactEmail) {
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
