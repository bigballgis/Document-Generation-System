package com.docgen.property;

import com.docgen.dto.BatchUpdateParameterRequest;
import com.docgen.dto.ParameterDTO;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.service.AggregationResolver;
import com.docgen.service.AuditLogService;
import com.docgen.service.ExpressionEngine;
import com.docgen.service.ParameterService;
import com.docgen.service.TemplateScanService;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 9: 批量更新原子性
 *
 * For any batch-update request where at least one item contains invalid data
 * (e.g., duplicate name, invalid data_type), the Batch_API SHALL reject the entire batch:
 * no parameter in the batch SHALL be modified, and the response SHALL contain all validation errors.
 *
 * <b>Validates: Requirements 9.4</b>
 */
class ParameterBatchUpdatePropertyTest {

    private static final Long TEMPLATE_ID = 100L;
    private static final Long TENANT_ID = 1L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeProperty
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterProperty
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * Property 9: Batch update atomicity — when a batch contains at least one invalid item,
     * the entire batch is rejected and no parameter is modified.
     *
     * <b>Validates: Requirements 9.4</b>
     */
    @Property(tries = 100)
    @Label("Feature: parameter-settings-ux, Property 9: Batch update atomicity")
    void batchUpdateWithInvalidItemRejectsEntireBatch(
            @ForAll("batchWithAtLeastOneInvalidItem") BatchScenario scenario
    ) {
        ParameterRepository repo = mock(ParameterRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);
        ParameterService service = new ParameterService(
                repo, mock(TemplateRepository.class), mock(TemplateScanService.class),
                expressionEngine, OBJECT_MAPPER, auditLogService, mock(AggregationResolver.class));

        // Setup: mock repository to return existing entities
        when(repo.findAllById(any())).thenReturn(scenario.existingEntities);
        when(repo.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(scenario.existingEntities);

        // Execute: attempt batch update — should throw
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchUpdate(TEMPLATE_ID, scenario.items));

        // Verify atomicity: no saves should have occurred
        verify(repo, never()).save(any(ParameterDefinition.class));

        // Verify error response contains validation errors
        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().isBlank());
    }

    /**
     * Property 9 (positive): When all items in a batch are valid,
     * all parameters are updated successfully.
     *
     * <b>Validates: Requirements 9.4</b>
     */
    @Property(tries = 100)
    @Label("Feature: parameter-settings-ux, Property 9: Batch update atomicity — all valid succeeds")
    void batchUpdateWithAllValidItemsSucceeds(
            @ForAll("batchWithAllValidItems") BatchScenario scenario
    ) {
        ParameterRepository repo = mock(ParameterRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);
        ParameterService service = new ParameterService(
                repo, mock(TemplateRepository.class), mock(TemplateScanService.class),
                expressionEngine, OBJECT_MAPPER, auditLogService, mock(AggregationResolver.class));

        when(repo.findAllById(any())).thenReturn(scenario.existingEntities);
        when(repo.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(scenario.existingEntities);
        when(repo.save(any(ParameterDefinition.class))).thenAnswer(inv -> {
            ParameterDefinition saved = inv.getArgument(0);
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        List<ParameterDTO> results = service.batchUpdate(TEMPLATE_ID, scenario.items);

        // All items should be updated
        assertEquals(scenario.items.size(), results.size());
        // Save should be called for each item
        verify(repo, times(scenario.items.size())).save(any(ParameterDefinition.class));
    }


    record BatchScenario(
            List<ParameterDefinition> existingEntities,
            List<BatchUpdateParameterRequest.BatchUpdateItem> items
    ) {}


    @Provide
    Arbitrary<BatchScenario> batchWithAtLeastOneInvalidItem() {
        return Arbitraries.integers().between(2, 6).flatMap(batchSize -> {
            // Pick which item index will be invalid (at least one)
            return Arbitraries.integers().between(0, batchSize - 1).flatMap(invalidIndex -> {
                return Arbitraries.of(InvalidReason.values()).map(reason -> {
                    List<ParameterDefinition> entities = new ArrayList<>();
                    List<BatchUpdateParameterRequest.BatchUpdateItem> items = new ArrayList<>();

                    for (int i = 0; i < batchSize; i++) {
                        long id = i + 1L;
                        String name = "param_" + i;
                        ParameterDefinition entity = makeEntity(id, TEMPLATE_ID, null, name, "REQUEST", "STRING");
                        entities.add(entity);

                        if (i == invalidIndex) {
                            // Create an invalid item based on the reason
                            items.add(createInvalidItem(id, reason, entities));
                        } else {
                            // Valid item: just update description
                            items.add(new BatchUpdateParameterRequest.BatchUpdateItem(
                                    id, 0, null, null, null, null, null,
                                    "updated desc " + i, null, null, null, null));
                        }
                    }

                    return new BatchScenario(entities, items);
                });
            });
        });
    }

    @Provide
    Arbitrary<BatchScenario> batchWithAllValidItems() {
        return Arbitraries.integers().between(1, 5).map(batchSize -> {
            List<ParameterDefinition> entities = new ArrayList<>();
            List<BatchUpdateParameterRequest.BatchUpdateItem> items = new ArrayList<>();

            for (int i = 0; i < batchSize; i++) {
                long id = i + 1L;
                String originalName = "param_" + i;
                ParameterDefinition entity = makeEntity(id, TEMPLATE_ID, null, originalName, "REQUEST", "STRING");
                entities.add(entity);

                // Valid update: change description only
                items.add(new BatchUpdateParameterRequest.BatchUpdateItem(
                        id, 0, null, null, null, null, null,
                        "new description " + i, null, null, null, null));
            }

            return new BatchScenario(entities, items);
        });
    }


    enum InvalidReason {
        INVALID_DATA_TYPE,
        INVALID_PARAMETER_TYPE,
        VERSION_MISMATCH
    }

    private BatchUpdateParameterRequest.BatchUpdateItem createInvalidItem(
            long id, InvalidReason reason, List<ParameterDefinition> entities) {
        return switch (reason) {
            case INVALID_DATA_TYPE -> new BatchUpdateParameterRequest.BatchUpdateItem(
                    id, 0, null, null, "INVALID_TYPE", null, null, null, null, null, null, null);
            case INVALID_PARAMETER_TYPE -> new BatchUpdateParameterRequest.BatchUpdateItem(
                    id, 0, null, "BOGUS_TYPE", null, null, null, null, null, null, null, null);
            case VERSION_MISMATCH -> {
                // Set a stale version that doesn't match the entity's current version
                ParameterDefinition entity = entities.stream()
                        .filter(e -> e.getId().equals(id)).findFirst().orElseThrow();
                int staleVersion = entity.getVersion() + 99;
                yield new BatchUpdateParameterRequest.BatchUpdateItem(
                        id, staleVersion, null, null, null, null, null, null, null, null, null, null);
            }
        };
    }


    private static ParameterDefinition makeEntity(Long id, Long templateId, Long parentId,
                                                   String name, String parameterType, String dataType) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(templateId);
        p.setParentId(parentId);
        p.setName(name);
        p.setParameterType(parameterType);
        p.setDataType(dataType);
        p.setRequired(false);
        p.setSortOrder(0);
        p.setVersion(0);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }
}

