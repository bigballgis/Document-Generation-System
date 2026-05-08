package com.docgen.dto.readiness;

/**
 * A missing business situation derived from branch, loop, or parameter coverage analysis.
 */
public class MissingBusinessSituationDTO {

    private String type;
    private String name;
    private String missingPath;

    public MissingBusinessSituationDTO() {}

    public MissingBusinessSituationDTO(String type, String name, String missingPath) {
        this.type = type;
        this.name = name;
        this.missingPath = missingPath;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMissingPath() {
        return missingPath;
    }

    public void setMissingPath(String missingPath) {
        this.missingPath = missingPath;
    }
}
