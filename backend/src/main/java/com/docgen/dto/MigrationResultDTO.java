package com.docgen.dto;

/**
 * DTO representing the result of migrating a traditional single-file template
 * to a Composite_Template.
 */
public class MigrationResultDTO {

    private Long compositeTemplateId;
    private Long segmentId;
    private int migratedDataSources;
    private int migratedExpressions;
    private int migratedVariableBindings;
    private Long archivedOriginalTemplateId;

    public MigrationResultDTO() {}

    public Long getCompositeTemplateId() { return compositeTemplateId; }
    public void setCompositeTemplateId(Long compositeTemplateId) { this.compositeTemplateId = compositeTemplateId; }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public int getMigratedDataSources() { return migratedDataSources; }
    public void setMigratedDataSources(int migratedDataSources) { this.migratedDataSources = migratedDataSources; }

    public int getMigratedExpressions() { return migratedExpressions; }
    public void setMigratedExpressions(int migratedExpressions) { this.migratedExpressions = migratedExpressions; }

    public int getMigratedVariableBindings() { return migratedVariableBindings; }
    public void setMigratedVariableBindings(int migratedVariableBindings) { this.migratedVariableBindings = migratedVariableBindings; }

    public Long getArchivedOriginalTemplateId() { return archivedOriginalTemplateId; }
    public void setArchivedOriginalTemplateId(Long archivedOriginalTemplateId) { this.archivedOriginalTemplateId = archivedOriginalTemplateId; }
}
