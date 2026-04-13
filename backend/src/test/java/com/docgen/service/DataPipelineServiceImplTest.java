package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.dto.PipelineStage.StageType;
import com.docgen.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataPipelineServiceImplTest {

    @Mock
    private DataAggregationService dataAggregationService;
    @Mock
    private ResponseTransformerService responseTransformerService;
    @Mock
    private ExpressionEngine expressionEngine;
    @Mock
    private DataValidationService dataValidationService;

    private DataPipelineServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataPipelineServiceImpl(
                dataAggregationService,
                responseTransformerService,
                expressionEngine,
                dataValidationService);
    }

    // ── executePipeline ──

    @Test
    void executePipeline_nullConfig_returnsParams() {
        Map<String, Object> params = Map.of("key", "value");
        Map<String, Object> result = service.executePipeline(null, params);
        assertEquals("value", result.get("key"));
    }

    @Test
    void executePipeline_emptyStages_returnsParams() {
        PipelineConfig config = new PipelineConfig(List.of());
        Map<String, Object> params = Map.of("x", 1);
        Map<String, Object> result = service.executePipeline(config, params);
        assertEquals(1, result.get("x"));
    }

    @Test
    void executePipeline_singleFetchStage() {
        PipelineStage fetch = new PipelineStage("fetch1", StageType.FETCH,
                Map.of("templateId", 1L), null);
        PipelineConfig config = new PipelineConfig(List.of(fetch));

        when(dataAggregationService.aggregateData(eq(1L), any()))
                .thenReturn(Map.of("name", "Alice"));

        Map<String, Object> result = service.executePipeline(config, Map.of());

        assertEquals("Alice", result.get("name"));
        verify(dataAggregationService).aggregateData(eq(1L), any());
    }

    @Test
    void executePipeline_fetchStageWithoutTemplateId_storesConfigData() {
        PipelineStage fetch = new PipelineStage("staticData", StageType.FETCH,
                Map.of("data", Map.of("greeting", "hello")), null);
        PipelineConfig config = new PipelineConfig(List.of(fetch));

        Map<String, Object> result = service.executePipeline(config, Map.of());

        assertEquals(Map.of("greeting", "hello"), result.get("staticData"));
    }

    @Test
    void executePipeline_dependencyOrder_executesInOrder() {
        // B depends on A, so A runs first
        PipelineStage stageA = new PipelineStage("A", StageType.FETCH,
                Map.of("data", Map.of("fromA", "valueA")), null);
        PipelineStage stageB = new PipelineStage("B", StageType.FETCH,
                Map.of("data", Map.of("fromB", "valueB")), List.of("A"));
        PipelineConfig config = new PipelineConfig(List.of(stageB, stageA));

        Map<String, Object> result = service.executePipeline(config, Map.of());

        assertEquals(Map.of("fromA", "valueA"), result.get("A"));
        assertEquals(Map.of("fromB", "valueB"), result.get("B"));
    }

    @Test
    void executePipeline_parallelIndependentStages() {
        // A and B have no dependencies — should both execute (potentially in parallel)
        PipelineStage stageA = new PipelineStage("A", StageType.FETCH,
                Map.of("data", Map.of("a", 1)), null);
        PipelineStage stageB = new PipelineStage("B", StageType.FETCH,
                Map.of("data", Map.of("b", 2)), null);
        PipelineConfig config = new PipelineConfig(List.of(stageA, stageB));

        Map<String, Object> result = service.executePipeline(config, Map.of());

        assertEquals(Map.of("a", 1), result.get("A"));
        assertEquals(Map.of("b", 2), result.get("B"));
    }

    @Test
    void executePipeline_stageFailure_abortsSubsequentStages() {
        PipelineStage stageA = new PipelineStage("A", StageType.FETCH,
                Map.of("templateId", 99L), null);
        PipelineStage stageB = new PipelineStage("B", StageType.FETCH,
                Map.of("data", Map.of("b", 1)), List.of("A"));
        PipelineConfig config = new PipelineConfig(List.of(stageA, stageB));

        when(dataAggregationService.aggregateData(eq(99L), any()))
                .thenThrow(new BusinessException("DATASOURCE_CONNECTION_FAILED",
                        "Connection refused", org.springframework.http.HttpStatus.BAD_GATEWAY));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executePipeline(config, Map.of()));
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void executePipeline_circularDependency_throwsException() {
        PipelineStage stageA = new PipelineStage("A", StageType.FETCH,
                Map.of(), List.of("B"));
        PipelineStage stageB = new PipelineStage("B", StageType.FETCH,
                Map.of(), List.of("A"));
        PipelineConfig config = new PipelineConfig(List.of(stageA, stageB));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executePipeline(config, Map.of()));
        assertEquals("PIPELINE_CIRCULAR_DEPENDENCY", ex.getErrorCode());
    }

    @Test
    void executePipeline_validateStageFailure_throwsBusinessException() {
        List<ValidationRule> rules = List.of(
                new ValidationRule("name", ValidationRule.RuleType.REQUIRED, null));
        PipelineStage validate = new PipelineStage("validate1", StageType.VALIDATE,
                Map.of("rules", rules), null);
        PipelineConfig config = new PipelineConfig(List.of(validate));

        DataValidationResult failResult = DataValidationResult.failure(List.of(
                new DataValidationError("name", ValidationRule.RuleType.REQUIRED, "Field 'name' is required")));
        when(dataValidationService.validate(any(), eq(rules))).thenReturn(failResult);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.executePipeline(config, Map.of()));
        assertTrue(ex.getMessage().contains("validate1"));
    }

    @Test
    void executePipeline_nullParams_usesEmptyMap() {
        PipelineStage stage = new PipelineStage("s1", StageType.FETCH,
                Map.of("data", Map.of("k", "v")), null);
        PipelineConfig config = new PipelineConfig(List.of(stage));

        Map<String, Object> result = service.executePipeline(config, null);

        assertEquals(Map.of("k", "v"), result.get("s1"));
    }

    // ── validatePipeline ──

    @Test
    void validatePipeline_nullConfig_returnsSuccess() {
        PipelineValidationResult result = service.validatePipeline(null);
        assertTrue(result.isValid());
    }

    @Test
    void validatePipeline_emptyStages_returnsSuccess() {
        PipelineConfig config = new PipelineConfig(List.of());
        PipelineValidationResult result = service.validatePipeline(config);
        assertTrue(result.isValid());
    }

    @Test
    void validatePipeline_noCycle_returnsSuccess() {
        PipelineStage a = new PipelineStage("A", StageType.FETCH, Map.of(), null);
        PipelineStage b = new PipelineStage("B", StageType.TRANSFORM, Map.of(), List.of("A"));
        PipelineStage c = new PipelineStage("C", StageType.COMPUTE, Map.of(), List.of("B"));
        PipelineConfig config = new PipelineConfig(List.of(a, b, c));

        PipelineValidationResult result = service.validatePipeline(config);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validatePipeline_directCycle_returnsFailure() {
        PipelineStage a = new PipelineStage("A", StageType.FETCH, Map.of(), List.of("B"));
        PipelineStage b = new PipelineStage("B", StageType.FETCH, Map.of(), List.of("A"));
        PipelineConfig config = new PipelineConfig(List.of(a, b));

        PipelineValidationResult result = service.validatePipeline(config);

        assertFalse(result.isValid());
        assertNotNull(result.getCyclePath());
        assertTrue(result.getCyclePath().size() >= 2);
    }

    @Test
    void validatePipeline_indirectCycle_returnsFailure() {
        PipelineStage a = new PipelineStage("A", StageType.FETCH, Map.of(), List.of("C"));
        PipelineStage b = new PipelineStage("B", StageType.TRANSFORM, Map.of(), List.of("A"));
        PipelineStage c = new PipelineStage("C", StageType.COMPUTE, Map.of(), List.of("B"));
        PipelineConfig config = new PipelineConfig(List.of(a, b, c));

        PipelineValidationResult result = service.validatePipeline(config);

        assertFalse(result.isValid());
        assertNotNull(result.getCyclePath());
        assertTrue(result.getErrors().get(0).contains("Circular dependency"));
    }

    @Test
    void validatePipeline_selfCycle_returnsFailure() {
        PipelineStage a = new PipelineStage("A", StageType.FETCH, Map.of(), List.of("A"));
        PipelineConfig config = new PipelineConfig(List.of(a));

        PipelineValidationResult result = service.validatePipeline(config);

        assertFalse(result.isValid());
        assertNotNull(result.getCyclePath());
    }

    @Test
    void validatePipeline_unknownDependency_returnsFailure() {
        PipelineStage a = new PipelineStage("A", StageType.FETCH, Map.of(), List.of("NONEXISTENT"));
        PipelineConfig config = new PipelineConfig(List.of(a));

        PipelineValidationResult result = service.validatePipeline(config);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("unknown stage"));
    }

    // ── topologicalLevels ──

    @Test
    void topologicalLevels_linearChain() {
        Map<String, PipelineStage> stageMap = new LinkedHashMap<>();
        stageMap.put("A", new PipelineStage("A", StageType.FETCH, Map.of(), null));
        stageMap.put("B", new PipelineStage("B", StageType.TRANSFORM, Map.of(), List.of("A")));
        stageMap.put("C", new PipelineStage("C", StageType.COMPUTE, Map.of(), List.of("B")));

        List<List<String>> levels = service.topologicalLevels(stageMap);

        assertEquals(3, levels.size());
        assertEquals(List.of("A"), levels.get(0));
        assertEquals(List.of("B"), levels.get(1));
        assertEquals(List.of("C"), levels.get(2));
    }

    @Test
    void topologicalLevels_parallelBranches() {
        Map<String, PipelineStage> stageMap = new LinkedHashMap<>();
        stageMap.put("A", new PipelineStage("A", StageType.FETCH, Map.of(), null));
        stageMap.put("B", new PipelineStage("B", StageType.FETCH, Map.of(), null));
        stageMap.put("C", new PipelineStage("C", StageType.COMPUTE, Map.of(), List.of("A", "B")));

        List<List<String>> levels = service.topologicalLevels(stageMap);

        assertEquals(2, levels.size());
        // First level has both A and B (independent)
        assertTrue(levels.get(0).containsAll(List.of("A", "B")));
        assertEquals(List.of("C"), levels.get(1));
    }

    @Test
    void topologicalLevels_allIndependent() {
        Map<String, PipelineStage> stageMap = new LinkedHashMap<>();
        stageMap.put("X", new PipelineStage("X", StageType.FETCH, Map.of(), null));
        stageMap.put("Y", new PipelineStage("Y", StageType.FETCH, Map.of(), null));
        stageMap.put("Z", new PipelineStage("Z", StageType.FETCH, Map.of(), null));

        List<List<String>> levels = service.topologicalLevels(stageMap);

        assertEquals(1, levels.size());
        assertEquals(3, levels.get(0).size());
    }
}
