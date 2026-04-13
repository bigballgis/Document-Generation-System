package com.docgen.service;

import com.docgen.dto.PipelineConfig;
import com.docgen.dto.PipelineValidationResult;

import java.util.Map;

/**
 * Orchestrates a four-stage data pipeline: Fetch → Transform → Compute → Validate.
 * <p>
 * Stages are executed in dependency order. Independent stages run in parallel.
 * If any stage fails, subsequent stages are aborted.
 * <p>
 * Validates: Requirements 34.1-34.7
 */
public interface DataPipelineService {

    /**
     * Execute the pipeline stages in dependency order.
     *
     * @param config the pipeline configuration
     * @param params runtime parameters
     * @return merged result data from all stages
     */
    Map<String, Object> executePipeline(PipelineConfig config, Map<String, Object> params);

    /**
     * Validate the pipeline configuration, checking for circular dependencies.
     *
     * @param config the pipeline configuration
     * @return validation result with errors if invalid
     */
    PipelineValidationResult validatePipeline(PipelineConfig config);
}
