package com.docgen.dto;

import java.util.List;
import java.util.Map;

/**
 * Represents a single stage in a data pipeline.
 * <p>
 * Each stage has a name, type (FETCH/TRANSFORM/COMPUTE/VALIDATE),
 * configuration parameters, and optional dependencies on other stages.
 */
public class PipelineStage {

    public enum StageType {
        FETCH, TRANSFORM, COMPUTE, VALIDATE
    }

    private String name;
    private StageType type;
    private Map<String, Object> config;
    private List<String> dependencies;

    public PipelineStage() {
    }

    public PipelineStage(String name, StageType type, Map<String, Object> config, List<String> dependencies) {
        this.name = name;
        this.type = type;
        this.config = config;
        this.dependencies = dependencies;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StageType getType() {
        return type;
    }

    public void setType(StageType type) {
        this.type = type;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }
}
