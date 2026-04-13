package com.docgen.dto;

/**
 * DTO representing a variable found in a segment template.
 */
public class SegmentVariableDTO {

    private String name;
    private String type;
    private boolean required;
    private String defaultValue;

    public SegmentVariableDTO() {}

    public SegmentVariableDTO(String name, String type, boolean required, String defaultValue) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
}
