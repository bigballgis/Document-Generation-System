package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a data source.
 */
public class CreateDataSourceRequest {

    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 100, message = "数据源名称不能超过100个字符")
    private String name;

    @NotBlank(message = "数据源类型不能为空")
    private String type;

    @NotNull(message = "配置信息不能为空")
    private String configJson;

    private boolean cacheEnabled = false;
    private Integer cacheTtl = 300;
    private int priority = 0;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public Integer getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Integer cacheTtl) { this.cacheTtl = cacheTtl; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
