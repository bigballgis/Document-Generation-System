package com.docgen.dto;

import java.util.List;

/**
 * Contains template metadata, data sources, expressions, and validation rules.
 * Used for JSON-based template config import/export (requirements 30.4, 30.5).
 */
public class TemplateConfigExport {

    private String version = "1.0";
    private TemplateMetadata template;
    private List<DataSourceExport> dataSources;
    private List<ExpressionExport> expressions;
    private List<VariableExport> variables;

    public TemplateConfigExport() {}

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public TemplateMetadata getTemplate() { return template; }
    public void setTemplate(TemplateMetadata template) { this.template = template; }

    public List<DataSourceExport> getDataSources() { return dataSources; }
    public void setDataSources(List<DataSourceExport> dataSources) { this.dataSources = dataSources; }

    public List<ExpressionExport> getExpressions() { return expressions; }
    public void setExpressions(List<ExpressionExport> expressions) { this.expressions = expressions; }

    public List<VariableExport> getVariables() { return variables; }
    public void setVariables(List<VariableExport> variables) { this.variables = variables; }

    /**
     * Template metadata subset for export (excludes IDs and tenant-specific data).
     */
    public static class TemplateMetadata {
        private String name;
        private String description;
        private String outputFormat;
        private String storageStrategy;
        private boolean async;
        private boolean reviewRequired;

        public TemplateMetadata() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getOutputFormat() { return outputFormat; }
        public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

        public String getStorageStrategy() { return storageStrategy; }
        public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }

        public boolean isAsync() { return async; }
        public void setAsync(boolean async) { this.async = async; }

        public boolean isReviewRequired() { return reviewRequired; }
        public void setReviewRequired(boolean reviewRequired) { this.reviewRequired = reviewRequired; }
    }

    /**
     * Data source configuration for export (excludes IDs and encrypted secrets).
     */
    public static class DataSourceExport {
        private String name;
        private String type;
        private String configJson;
        private boolean cacheEnabled;
        private Integer cacheTtl;
        private int priority;

        public DataSourceExport() {}

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

    /**
     * Expression configuration for export.
     */
    public static class ExpressionExport {
        private String name;
        private String expressionType;
        private String expressionText;
        private String description;
        private int executionOrder;

        public ExpressionExport() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getExpressionType() { return expressionType; }
        public void setExpressionType(String expressionType) { this.expressionType = expressionType; }

        public String getExpressionText() { return expressionText; }
        public void setExpressionText(String expressionText) { this.expressionText = expressionText; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public int getExecutionOrder() { return executionOrder; }
        public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    }

    /**
     * Template variable configuration for export.
     */
    public static class VariableExport {
        private String name;
        private String variableType;
        private String defaultValue;
        private String description;
        private String bindingSource;
        private String bindingField;
        private boolean bound;

        public VariableExport() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVariableType() { return variableType; }
        public void setVariableType(String variableType) { this.variableType = variableType; }

        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getBindingSource() { return bindingSource; }
        public void setBindingSource(String bindingSource) { this.bindingSource = bindingSource; }

        public String getBindingField() { return bindingField; }
        public void setBindingField(String bindingField) { this.bindingField = bindingField; }

        public boolean isBound() { return bound; }
        public void setBound(boolean bound) { this.bound = bound; }
    }
}
