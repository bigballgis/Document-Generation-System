package com.docgen.service;

import com.docgen.dto.BatchUpdateParameterRequest;
import com.docgen.dto.ParameterDTO;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ParameterService} batch operations (batchDelete and batchUpdate).
 * Validates: Requirements 9.1, 9.2, 9.3, 9.4
 */
@ExtendWith(MockitoExtension.class)
class ParameterServiceBatchTest {

    @Mock
    private ParameterRepository parameterRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private TemplateScanService templateScanService;

    @Mock
    private ExpressionEngine expressionEngine;

    @Mock
    private AuditLogService auditLogService;

    private ObjectMapper objectMapper;
    private ParameterService parameterService;

    private static final Long TEMPLATE_ID = 100L;
    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parameterService = new ParameterService(
                parameterRepository, templateRepository, templateScanService,
                expressionEngine, objectMapper, auditLogService, mock(AggregationResolver.class));
        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── batchDelete Tests ── Validates: Requirements 9.1, 9.3

    @Nested
    class BatchDeleteTests {

        @Test
        void batchDelete_normalDelete_success() {
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");
            ParameterDefinition p2 = makeEntity(2L, TEMPLATE_ID, null, "param2", "REQUEST", "NUMBER");

            when(parameterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

            parameterService.batchDelete(TEMPLATE_ID, List.of(1L, 2L));

            verify(parameterRepository).deleteAll(argThat(list -> {
                List<ParameterDefinition> items = (List<ParameterDefinition>) list;
                return items.size() == 2;
            }));
            verify(auditLogService).log(eq(TENANT_ID), isNull(), eq("BATCH_DELETE_PARAMETER"),
                    eq("PARAMETER"), eq(TEMPLATE_ID), any(String.class), isNull());
        }

        @Test
        void batchDelete_invalidIds_throwsBusinessException() {
            // ID 99 does not exist
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");
            when(parameterRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(p1));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.batchDelete(TEMPLATE_ID, List.of(1L, 99L)));

            assertEquals("PARAMETER_BATCH_INVALID_IDS", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("99"));
            verify(parameterRepository, never()).deleteAll(any());
        }

        @Test
        void batchDelete_crossTemplateIds_throwsBusinessException() {
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");
            ParameterDefinition p2 = makeEntity(2L, 999L, null, "param2", "REQUEST", "STRING"); // different template

            when(parameterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.batchDelete(TEMPLATE_ID, List.of(1L, 2L)));

            assertEquals("PARAMETER_BATCH_INVALID_IDS", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("2"));
            verify(parameterRepository, never()).deleteAll(any());
        }

        @Test
        void batchDelete_cascadeChildParameters_onlyDeletesTopLevel() {
            // Parent (id=1) with child (id=2) — both in the delete list
            ParameterDefinition parent = makeEntity(1L, TEMPLATE_ID, null, "company", "REQUEST", "OBJECT");
            ParameterDefinition child = makeEntity(2L, TEMPLATE_ID, 1L, "name", "REQUEST", "STRING");

            when(parameterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(parent, child));

            parameterService.batchDelete(TEMPLATE_ID, List.of(1L, 2L));

            // Only the parent should be deleted (child handled by CASCADE)
            verify(parameterRepository).deleteAll(argThat(list -> {
                List<ParameterDefinition> items = (List<ParameterDefinition>) list;
                return items.size() == 1 && items.get(0).getId().equals(1L);
            }));
        }

        @Test
        void batchDelete_deepNestedCascade_onlyDeletesTopLevel() {
            // Grandparent (id=1) -> Parent (id=2) -> Child (id=3), all in delete list
            ParameterDefinition grandparent = makeEntity(1L, TEMPLATE_ID, null, "root", "REQUEST", "OBJECT");
            ParameterDefinition parent = makeEntity(2L, TEMPLATE_ID, 1L, "mid", "REQUEST", "OBJECT");
            ParameterDefinition child = makeEntity(3L, TEMPLATE_ID, 2L, "leaf", "REQUEST", "STRING");

            when(parameterRepository.findAllById(List.of(1L, 2L, 3L)))
                    .thenReturn(List.of(grandparent, parent, child));

            parameterService.batchDelete(TEMPLATE_ID, List.of(1L, 2L, 3L));

            // Only grandparent should be deleted
            verify(parameterRepository).deleteAll(argThat(list -> {
                List<ParameterDefinition> items = (List<ParameterDefinition>) list;
                return items.size() == 1 && items.get(0).getId().equals(1L);
            }));
        }
    }

    // ── batchUpdate Tests ── Validates: Requirements 9.2, 9.4

    @Nested
    class BatchUpdateTests {

        @Test
        void batchUpdate_normalUpdate_success() {
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");
            ParameterDefinition p2 = makeEntity(2L, TEMPLATE_ID, null, "param2", "REQUEST", "NUMBER");

            when(parameterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(p1, p2));
            when(parameterRepository.save(any(ParameterDefinition.class)))
                    .thenAnswer(inv -> {
                        ParameterDefinition saved = inv.getArgument(0);
                        saved.setUpdatedAt(Instant.now());
                        return saved;
                    });

            List<BatchUpdateParameterRequest.BatchUpdateItem> items = List.of(
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            1L, 0, "renamed1", null, null, null, null, "updated desc", null, null, null, null),
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            2L, 0, "renamed2", null, null, true, null, null, null, null, null, null)
            );

            List<ParameterDTO> results = parameterService.batchUpdate(TEMPLATE_ID, items);

            assertEquals(2, results.size());
            assertEquals("renamed1", results.get(0).getName());
            assertEquals("updated desc", results.get(0).getDescription());
            assertEquals("renamed2", results.get(1).getName());
            assertTrue(results.get(1).isRequired());
            verify(parameterRepository, times(2)).save(any(ParameterDefinition.class));
            verify(auditLogService).log(eq(TENANT_ID), isNull(), eq("BATCH_UPDATE_PARAMETER"),
                    eq("PARAMETER"), eq(TEMPLATE_ID), any(String.class), isNull());
        }

        @Test
        void batchUpdate_validationFailure_rejectsEntireBatch() {
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");
            ParameterDefinition p2 = makeEntity(2L, TEMPLATE_ID, null, "param2", "REQUEST", "STRING");

            when(parameterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(p1, p2));

            // Item 2 has invalid data type
            List<BatchUpdateParameterRequest.BatchUpdateItem> items = List.of(
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            1L, 0, "valid_name", null, null, null, null, null, null, null, null, null),
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            2L, 0, null, null, "INVALID_TYPE", null, null, null, null, null, null, null)
            );

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.batchUpdate(TEMPLATE_ID, items));

            assertEquals("PARAMETER_BATCH_VALIDATION_FAILED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("无效的数据类型"));
            // No saves should have occurred since validation failed before apply phase
            verify(parameterRepository, never()).save(any(ParameterDefinition.class));
        }

        @Test
        void batchUpdate_optimisticLockConflict_throwsException() {
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");
            p1.setVersion(5); // current version is 5

            when(parameterRepository.findAllById(List.of(1L))).thenReturn(List.of(p1));

            // Request has version 3 (stale)
            List<BatchUpdateParameterRequest.BatchUpdateItem> items = List.of(
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            1L, 3, "newName", null, null, null, null, null, null, null, null, null)
            );

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.batchUpdate(TEMPLATE_ID, items));

            assertEquals("PARAMETER_CONCURRENT_MODIFICATION", ex.getErrorCode());
            verify(parameterRepository, never()).save(any(ParameterDefinition.class));
        }

        @Test
        void batchUpdate_partialInvalidItems_rejectsEntireBatch() {
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");

            when(parameterRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(p1));

            List<BatchUpdateParameterRequest.BatchUpdateItem> items = List.of(
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            1L, 0, "valid_name", null, null, null, null, null, null, null, null, null),
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            99L, 0, "ghost", null, null, null, null, null, null, null, null, null)
            );

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.batchUpdate(TEMPLATE_ID, items));

            assertEquals("PARAMETER_BATCH_INVALID_IDS", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("99"));
            verify(parameterRepository, never()).save(any(ParameterDefinition.class));
        }

        @Test
        void batchUpdate_crossTemplateId_rejectsEntireBatch() {
            ParameterDefinition p1 = makeEntity(1L, TEMPLATE_ID, null, "param1", "REQUEST", "STRING");
            ParameterDefinition p2 = makeEntity(2L, 999L, null, "param2", "REQUEST", "STRING"); // different template

            when(parameterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(p1, p2));

            List<BatchUpdateParameterRequest.BatchUpdateItem> items = List.of(
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            1L, 0, null, null, null, null, null, null, null, null, null, null),
                    new BatchUpdateParameterRequest.BatchUpdateItem(
                            2L, 0, null, null, null, null, null, null, null, null, null, null)
            );

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.batchUpdate(TEMPLATE_ID, items));

            assertEquals("PARAMETER_BATCH_INVALID_IDS", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("2"));
            verify(parameterRepository, never()).save(any(ParameterDefinition.class));
        }
    }

    // ── Helper ──

    private ParameterDefinition makeEntity(Long id, Long templateId, Long parentId,
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
