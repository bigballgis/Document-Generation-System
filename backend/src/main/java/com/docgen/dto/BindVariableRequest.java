package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for binding a template variable to a data source field or expression result.
 */
public class BindVariableRequest {

    @NotBlank(message = "绑定来源不能为空")
    @Size(max = 20, message = "绑定来源不能超过20个字符")
    private String bindingSource;

    @NotBlank(message = "绑定字段不能为空")
    @Size(max = 200, message = "绑定字段不能超过200个字符")
    private String bindingField;

    public String getBindingSource() { return bindingSource; }
    public void setBindingSource(String bindingSource) { this.bindingSource = bindingSource; }

    public String getBindingField() { return bindingField; }
    public void setBindingField(String bindingField) { this.bindingField = bindingField; }
}
