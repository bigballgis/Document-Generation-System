package com.docgen.service;

import com.docgen.dto.PipelineConfig;
import com.docgen.dto.PipelineStage;
import com.docgen.dto.PipelineStage.StageType;
import com.docgen.dto.PipelineValidationResult;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of the four-stage data pipeline: Fetch → Transform → Compute → Validate.
 * <p>
 * Stages are resolved in topological (dependency) order using DFS.
 * Independent stages within the same level execute in parallel.
 * If any stage fails, subsequent stages are aborted.
 * <p>
 * Validates: Requirements 34.1-34.7
 */
@Service
public class DataPipelineServiceImpl implements DataPipelineService {

    private static final Logger log = LoggerFactory.getLogger(DataPipelineServiceImpl.class);

    private final DataAggregationService dataAggregationService;
    private final ResponseTransformerService responseTransformerService;
    private final ExpressionEngine expressionEngine;
    private final DataValidationService dataValidationService;

    public DataPipelineServiceImpl(DataAggregationService dataAggregationService,
                                   ResponseTransformerService responseTransformerService,
                                   ExpressionEngine expressionEngine,
                                   DataValidationService dataValidationService) {
        this.dataAggregationService = dataAggregationService;
        this.responseTransformerService = responseTransformerService;
        this.expressionEngine = expressionEngine;
        this.dataValidationService = dataValidationService;
    }

    @Override
    public Map<String, Object> executePipeline(PipelineConfig config, Map<String, Object> params) {
        if (config == null || config.getStages() == null || config.getStages().isEmpty()) {
            return params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
        }

        // Validate first — reject circular dependencies
        PipelineValidationResult validation = validatePipeline(config);
        if (!validation.isValid()) {
            throw new BusinessException(ErrorCode.PIPELINE_CIRCULAR_DEPENDENCY,
                    validation.getErrors().get(0), HttpStatus.BAD_REQUEST);
        }

        // Build stage lookup
        Map<String, PipelineStage> stageMap = new LinkedHashMap<>();
        for (PipelineStage stage : config.getStages()) {
            stageMap.put(stage.getName(), stage);
        }

        // Topological sort to get execution order (levels for parallelism)
        List<List<String>> executionLevels = topologicalLevels(stageMap);

        // Execute level by level
        Map<String, Object> context = new ConcurrentHashMap<>(params != null ? params : Map.of());

        for (List<String> level : executionLevels) {
            if (level.size() == 1) {
                // Single stage — execute directly
                executeStage(stageMap.get(level.get(0)), context);
            } else {
                // Multiple independent stages — execute in parallel
                List<CompletableFuture<Void>> futures = level.stream()
                        .map(name -> CompletableFuture.runAsync(() ->
                                executeStage(stageMap.get(name), context)))
                        .collect(Collectors.toList());

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException be) {
                        throw be;
                    }
                    throw new BusinessException(ErrorCode.PIPELINE_STAGE_FAILED,
                            "Pipeline stage failed: " + cause.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, cause);
                }
            }
        }

        return new LinkedHashMap<>(context);
    }

    @Override
    public PipelineValidationResult validatePipeline(PipelineConfig config) {
        if (config == null || config.getStages() == null || config.getStages().isEmpty()) {
            return PipelineValidationResult.success();
        }

        Map<String, PipelineStage> stageMap = new LinkedHashMap<>();
        for (PipelineStage stage : config.getStages()) {
            stageMap.put(stage.getName(), stage);
        }

        // Check for references to non-existent stages
        List<String> errors = new ArrayList<>();
        for (PipelineStage stage : config.getStages()) {
            if (stage.getDependencies() != null) {
                for (String dep : stage.getDependencies()) {
                    if (!stageMap.containsKey(dep)) {
                        errors.add("Stage '" + stage.getName() + "' depends on unknown stage '" + dep + "'");
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            return PipelineValidationResult.failure(errors);
        }

        // Detect circular dependencies using DFS
        List<String> cyclePath = detectCycle(stageMap);
        if (cyclePath != null) {
            return PipelineValidationResult.circularDependency(cyclePath);
        }

        return PipelineValidationResult.success();
    }

    // ── Stage execution ──

    void executeStage(PipelineStage stage, Map<String, Object> context) {
        log.info("Executing pipeline stage '{}' (type={})", stage.getName(), stage.getType());
        try {
            Map<String, Object> result = switch (stage.getType()) {
                case FETCH -> executeFetch(stage, context);
                case TRANSFORM -> executeTransform(stage, context);
                case COMPUTE -> executeCompute(stage, context);
                case VALIDATE -> executeValidate(stage, context);
            };
            if (result != null) {
                context.putAll(result);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pipeline stage '{}' failed: {}", stage.getName(), e.getMessage());
            throw new BusinessException(ErrorCode.PIPELINE_STAGE_FAILED,
                    "Stage '" + stage.getName() + "' failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeFetch(PipelineStage stage, Map<String, Object> context) {
        Map<String, Object> config = stage.getConfig();
        if (config == null) {
            return Map.of();
        }
        Long templateId = config.get("templateId") != null
                ? ((Number) config.get("templateId")).longValue() : null;
        if (templateId != null) {
            return dataAggregationService.aggregateData(templateId, new HashMap<>(context));
        }
        // If no templateId, store the config data directly under the stage name
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(stage.getName(), config.getOrDefault("data", Map.of()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeTransform(PipelineStage stage, Map<String, Object> context) {
        Map<String, Object> config = stage.getConfig();
        if (config == null) {
            return Map.of();
        }
        Object rules = config.get("rules");
        Object sourceData = config.get("sourceField") != null
                ? context.get(config.get("sourceField")) : context;
        if (rules instanceof List<?> ruleList && sourceData != null) {
            List<com.docgen.dto.TransformRule> transformRules = new ArrayList<>();
            for (Object r : ruleList) {
                if (r instanceof com.docgen.dto.TransformRule tr) {
                    transformRules.add(tr);
                }
            }
            if (!transformRules.isEmpty()) {
                return responseTransformerService.transform(sourceData, transformRules);
            }
        }
        // Fallback: store config data under stage name
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(stage.getName(), config.getOrDefault("data", Map.of()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeCompute(PipelineStage stage, Map<String, Object> context) {
        Map<String, Object> config = stage.getConfig();
        if (config == null) {
            return Map.of();
        }
        Object exprs = config.get("expressions");
        if (exprs instanceof List<?> exprList) {
            List<ExpressionEngine.ExpressionConfig> expressionConfigs = new ArrayList<>();
            for (Object e : exprList) {
                if (e instanceof ExpressionEngine.ExpressionConfig ec) {
                    expressionConfigs.add(ec);
                }
            }
            if (!expressionConfigs.isEmpty()) {
                return expressionEngine.evaluateAll(expressionConfigs, new HashMap<>(context));
            }
        }
        // Fallback: store config data under stage name
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(stage.getName(), config.getOrDefault("data", Map.of()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeValidate(PipelineStage stage, Map<String, Object> context) {
        Map<String, Object> config = stage.getConfig();
        if (config == null) {
            return Map.of();
        }
        Object rules = config.get("rules");
        if (rules instanceof List<?> ruleList) {
            List<com.docgen.dto.ValidationRule> validationRules = new ArrayList<>();
            for (Object r : ruleList) {
                if (r instanceof com.docgen.dto.ValidationRule vr) {
                    validationRules.add(vr);
                }
            }
            if (!validationRules.isEmpty()) {
                var result = dataValidationService.validate(new HashMap<>(context), validationRules);
                if (!result.isValid()) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "Validation stage '" + stage.getName() + "' failed: " + result.getErrors(),
                            HttpStatus.BAD_REQUEST);
                }
            }
        }
        return Map.of();
    }

    // ── Topological sort (Kahn's algorithm) ──

    List<List<String>> topologicalLevels(Map<String, PipelineStage> stageMap) {
        // Build in-degree map and adjacency list
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();

        for (String name : stageMap.keySet()) {
            inDegree.put(name, 0);
            dependents.put(name, new ArrayList<>());
        }

        for (PipelineStage stage : stageMap.values()) {
            List<String> deps = stage.getDependencies();
            if (deps != null) {
                for (String dep : deps) {
                    if (stageMap.containsKey(dep)) {
                        inDegree.merge(stage.getName(), 1, Integer::sum);
                        dependents.get(dep).add(stage.getName());
                    }
                }
            }
        }

        List<List<String>> levels = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        // Start with nodes that have no dependencies
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            List<String> currentLevel = new ArrayList<>(queue);
            queue.clear();
            levels.add(currentLevel);

            for (String node : currentLevel) {
                for (String dependent : dependents.get(node)) {
                    int newDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newDegree);
                    if (newDegree == 0) {
                        queue.add(dependent);
                    }
                }
            }
        }

        return levels;
    }

    // ── Cycle detection (DFS) ──

    List<String> detectCycle(Map<String, PipelineStage> stageMap) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new LinkedHashSet<>();

        for (String name : stageMap.keySet()) {
            List<String> cycle = dfs(name, stageMap, visited, inStack);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private List<String> dfs(String node, Map<String, PipelineStage> stageMap,
                             Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node)) {
            // Build cycle path
            List<String> cycle = new ArrayList<>();
            boolean found = false;
            for (String s : inStack) {
                if (s.equals(node)) {
                    found = true;
                }
                if (found) {
                    cycle.add(s);
                }
            }
            cycle.add(node); // close the cycle
            return cycle;
        }
        if (visited.contains(node)) {
            return null;
        }

        visited.add(node);
        inStack.add(node);

        PipelineStage stage = stageMap.get(node);
        if (stage != null && stage.getDependencies() != null) {
            for (String dep : stage.getDependencies()) {
                if (stageMap.containsKey(dep)) {
                    List<String> cycle = dfs(dep, stageMap, visited, inStack);
                    if (cycle != null) {
                        return cycle;
                    }
                }
            }
        }

        inStack.remove(node);
        return null;
    }
}
