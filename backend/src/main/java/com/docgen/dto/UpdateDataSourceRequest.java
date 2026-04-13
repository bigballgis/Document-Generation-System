package com.docgen.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating a data source.
 * All fields are optional; only non-null fields are applied.
 */
public class UpdateDataSourceRequest {

    @Size(max = 100, message = "数据源名称不能超过100个字符")
    private String name;

    private String type;
    private String configJson;
    private Boolean cacheEnabled;
    private Integer cacheTtl;
    private Integer priority;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public Boolean getCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(Boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public Integer getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Integer cacheTtl) { this.cacheTtl = cacheTtl; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
