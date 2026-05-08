package com.docgen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CreateTenantRequest {

    @NotBlank(message = "Tenant name must not be blank")
    private String name;

    private String contactName;

    @Email(message = "Contact email format is invalid")
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
