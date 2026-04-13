package com.docgen.dto;

import java.util.List;

/**
 * Configuration for a data processing pipeline.
 * <p>
 * Contains an ordered list of stages that define the pipeline's
 * Fetch → Transform → Compute → Validate flow.
 */
public class PipelineConfig {

    private List<PipelineStage> stages;

    public PipelineConfig() {
    }

    public PipelineConfig(List<PipelineStage> stages) {
        this.stages = stages;
    }

    public List<PipelineStage> getStages() {
        return stages;
    }

    public void setStages(List<PipelineStage> stages) {
        this.stages = stages;
    }
}
