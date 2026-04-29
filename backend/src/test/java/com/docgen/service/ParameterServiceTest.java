package com.docgen.service;

import com.docgen.dto.CreateParameterRequest;
import com.docgen.dto.ExpressionValidationResult;
import com.docgen.dto.ParameterDTO;
import com.docgen.dto.UpdateParameterRequest;
import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ParameterService}.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.14
 */
@ExtendWith(MockitoExtension.class)
class ParameterServiceTest {

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

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parameterService = new ParameterService(parameterRepository, templateRepository, templateScanService, expressionEngine, objectMapper, auditLogService, mock(AggregationResolver.class));
    }


    @Test
    void createParameter_rootRequest_success() {
        CreateParameterRequest req = new CreateParameterRequest(
                "userName", "REQUEST", "STRING", true, "default",
                "User name", 1, null, null, null, null);

        when(parameterRepository.existsByTemplateIdAndParentIdIsNullAndName(TEMPLATE_ID, "userName"))
                .thenReturn(false);
        when(parameterRepository.save(any(ParameterDefinition.class))).thenAnswer(inv -> {
            ParameterDefinition p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        ParameterDTO result = parameterService.createParameter(TEMPLATE_ID, req);

        assertEquals("userName", result.getName());
        assertEquals("REQUEST", result.getParameterType());
        assertEquals("STRING", result.getDataType());
        assertTrue(result.isRequired());
        assertEquals("default", result.getDefaultValue());
        assertNull(result.getParentId());
        verify(parameterRepository).save(any(ParameterDefinition.class));
    }


    @Test
    void createParameter_childUnderObject_success() {
        ParameterDefinition parent = makeEntity(10L, TEMPLATE_ID, null, "company", "REQUEST", "OBJECT");

        CreateParameterRequest req = new CreateParameterRequest(
                "name", "REQUEST", "STRING", false, null,
                "Company name", 0, null, null, null, 10L);

        when(parameterRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(parameterRepository.existsByTemplateIdAndParentIdAndName(TEMPLATE_ID, 10L, "name"))
                .thenReturn(false);
        when(parameterRepository.save(any(ParameterDefinition.class))).thenAnswer(inv -> {
            ParameterDefinition p = inv.getArgument(0);
            p.setId(11L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        ParameterDTO result = parameterService.createParameter(TEMPLATE_ID, req);

        assertEquals("name", result.getName());
        assertEquals(10L, result.getParentId());
        verify(parameterRepository, atLeastOnce()).findById(10L);
    }


    @Test
    void getParameterTree_nestedStructure() {
        ParameterDefinition root = makeEntity(1L, TEMPLATE_ID, null, "company", "REQUEST", "OBJECT");
        ParameterDefinition child = makeEntity(2L, TEMPLATE_ID, 1L, "address", "REQUEST", "OBJECT");
        ParameterDefinition grandchild = makeEntity(3L, TEMPLATE_ID, 2L, "city", "REQUEST", "STRING");

        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(List.of(root, child, grandchild));

        List<ParameterDTO> tree = parameterService.getParameterTree(TEMPLATE_ID);

        assertEquals(1, tree.size());
        ParameterDTO rootDto = tree.get(0);
        assertEquals("company", rootDto.getName());
        assertEquals("company", rootDto.getParameterPath());
        assertEquals(1, rootDto.getChildren().size());

        ParameterDTO childDto = rootDto.getChildren().get(0);
        assertEquals("address", childDto.getName());
        assertEquals("company.address", childDto.getParameterPath());
        assertEquals(1, childDto.getChildren().size());

        ParameterDTO grandchildDto = childDto.getChildren().get(0);
        assertEquals("city", grandchildDto.getName());
        assertEquals("company.address.city", grandchildDto.getParameterPath());
        assertTrue(grandchildDto.getChildren().isEmpty());
    }


    @Test
    void getParameterFlat_computedPaths() {
        ParameterDefinition root = makeEntity(1L, TEMPLATE_ID, null, "company", "REQUEST", "OBJECT");
        ParameterDefinition child = makeEntity(2L, TEMPLATE_ID, 1L, "name", "REQUEST", "STRING");

        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(List.of(root, child));

        List<ParameterDTO> flat = parameterService.getParameterFlat(TEMPLATE_ID);

        assertEquals(2, flat.size());
        assertEquals("company", flat.get(0).getParameterPath());
        assertEquals("company.name", flat.get(1).getParameterPath());
        // flat list has no children nesting
        assertNull(flat.get(0).getChildren());
        assertNull(flat.get(1).getChildren());
    }


    @Test
    void updateParameter_success() {
        ParameterDefinition existing = makeEntity(1L, TEMPLATE_ID, null, "oldName", "REQUEST", "STRING");

        when(parameterRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(parameterRepository.existsByTemplateIdAndParentIdIsNullAndName(TEMPLATE_ID, "newName"))
                .thenReturn(false);
        when(parameterRepository.save(any(ParameterDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateParameterRequest req = new UpdateParameterRequest(
                "newName", null, null, true, "val", "desc", 5,
                null, null, null, 0);

        ParameterDTO result = parameterService.updateParameter(1L, req);

        assertEquals("newName", result.getName());
        assertTrue(result.isRequired());
        assertEquals("val", result.getDefaultValue());
        assertEquals("desc", result.getDescription());
        assertEquals(5, result.getSortOrder());
    }


    @Test
    void updateParameter_optimisticLockConflict() {
        ParameterDefinition existing = makeEntity(1L, TEMPLATE_ID, null, "param", "REQUEST", "STRING");

        when(parameterRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(parameterRepository.save(any(ParameterDefinition.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        ParameterDefinition.class, 1L));

        UpdateParameterRequest req = new UpdateParameterRequest(
                null, null, null, null, null, null, null,
                null, null, null, 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parameterService.updateParameter(1L, req));
        assertEquals("PARAMETER_CONCURRENT_MODIFICATION", ex.getErrorCode());
    }


    @Test
    void deleteParameter_success() {
        ParameterDefinition entity = makeEntity(1L, TEMPLATE_ID, null, "toDelete", "REQUEST", "STRING");
        when(parameterRepository.findById(1L)).thenReturn(Optional.of(entity));

        parameterService.deleteParameter(1L);

        verify(parameterRepository).delete(entity);
    }


    @Test
    void deleteParameter_notFound_throws() {
        when(parameterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> parameterService.deleteParameter(99L));
    }


    @Test
    void createParameter_parentNotFound_throws() {
        CreateParameterRequest req = new CreateParameterRequest(
                "child", "REQUEST", "STRING", false, null,
                null, 0, null, null, null, 999L);

        when(parameterRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parameterService.createParameter(TEMPLATE_ID, req));
        assertEquals("PARAMETER_INVALID_PARENT", ex.getErrorCode());
    }


    @Test
    void createParameter_parentWrongTemplate_throws() {
        ParameterDefinition parent = makeEntity(10L, 999L, null, "other", "REQUEST", "OBJECT");

        CreateParameterRequest req = new CreateParameterRequest(
                "child", "REQUEST", "STRING", false, null,
                null, 0, null, null, null, 10L);

        when(parameterRepository.findById(10L)).thenReturn(Optional.of(parent));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parameterService.createParameter(TEMPLATE_ID, req));
        assertEquals("PARAMETER_INVALID_PARENT", ex.getErrorCode());
    }


    @Test
    void createParameter_parentNotContainerType_throws() {
        ParameterDefinition parent = makeEntity(10L, TEMPLATE_ID, null, "leaf", "REQUEST", "STRING");

        CreateParameterRequest req = new CreateParameterRequest(
                "child", "REQUEST", "STRING", false, null,
                null, 0, null, null, null, 10L);

        when(parameterRepository.findById(10L)).thenReturn(Optional.of(parent));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parameterService.createParameter(TEMPLATE_ID, req));
        assertEquals("PARAMETER_PARENT_TYPE_INVALID", ex.getErrorCode());
    }


    @Test
    void getParameterTree_emptyTemplate_returnsEmptyList() {
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(Collections.emptyList());

        List<ParameterDTO> tree = parameterService.getParameterTree(TEMPLATE_ID);

        assertTrue(tree.isEmpty());
    }

    @Test
    void getParameterFlat_emptyTemplate_returnsEmptyList() {
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(Collections.emptyList());

        List<ParameterDTO> flat = parameterService.getParameterFlat(TEMPLATE_ID);

        assertTrue(flat.isEmpty());
    }


    @Test
    void createParameter_duplicateName_throws() {
        CreateParameterRequest req = new CreateParameterRequest(
                "dup", "REQUEST", "STRING", false, null,
                null, 0, null, null, null, null);

        when(parameterRepository.existsByTemplateIdAndParentIdIsNullAndName(TEMPLATE_ID, "dup"))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parameterService.createParameter(TEMPLATE_ID, req));
        assertEquals("PARAMETER_DUPLICATE_NAME", ex.getErrorCode());
    }

    @Test
    void createParameter_derivedWithExpression_success() {
        CreateParameterRequest req = new CreateParameterRequest(
                "total", "DERIVED", "NUMBER", false, null,
                "Computed total", 0, "price * qty", "JAVASCRIPT",
                null, null);

        when(expressionEngine.validateExpression("price * qty", ExpressionType.JAVASCRIPT))
                .thenReturn(ExpressionValidationResult.success());
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(Collections.emptyList());
        when(parameterRepository.existsByTemplateIdAndParentIdIsNullAndName(TEMPLATE_ID, "total"))
                .thenReturn(false);
        when(parameterRepository.save(any(ParameterDefinition.class))).thenAnswer(inv -> {
            ParameterDefinition p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        ParameterDTO result = parameterService.createParameter(TEMPLATE_ID, req);

        assertEquals("DERIVED", result.getParameterType());
        assertEquals("price * qty", result.getExpressionText());
        assertEquals("JAVASCRIPT", result.getExpressionType());
    }

    @Test
    void createParameter_derivedMissingExpression_throws() {
        CreateParameterRequest req = new CreateParameterRequest(
                "total", "DERIVED", "NUMBER", false, null,
                null, 0, null, "JAVASCRIPT", null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> parameterService.createParameter(TEMPLATE_ID, req));
        assertEquals("PARAMETER_EXPRESSION_REQUIRED", ex.getErrorCode());
    }

    @Test
    void createParameter_defaultsApplied() {
        // parameterType and dataType default to REQUEST / STRING when null
        CreateParameterRequest req = new CreateParameterRequest(
                "simple", null, null, null, null,
                null, null, null, null, null, null);

        when(parameterRepository.existsByTemplateIdAndParentIdIsNullAndName(TEMPLATE_ID, "simple"))
                .thenReturn(false);
        when(parameterRepository.save(any(ParameterDefinition.class))).thenAnswer(inv -> {
            ParameterDefinition p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        ParameterDTO result = parameterService.createParameter(TEMPLATE_ID, req);

        assertEquals("REQUEST", result.getParameterType());
        assertEquals("STRING", result.getDataType());
        assertFalse(result.isRequired());
        assertEquals(0, result.getSortOrder());
    }

    @Test
    void createParameter_withValidationRules_success() {
        Map<String, Object> rules = Map.of("min_length", 1, "max_length", 100);
        CreateParameterRequest req = new CreateParameterRequest(
                "validated", "REQUEST", "STRING", false, null,
                null, 0, null, null, rules, null);

        when(parameterRepository.existsByTemplateIdAndParentIdIsNullAndName(TEMPLATE_ID, "validated"))
                .thenReturn(false);
        when(parameterRepository.save(any(ParameterDefinition.class))).thenAnswer(inv -> {
            ParameterDefinition p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        ParameterDTO result = parameterService.createParameter(TEMPLATE_ID, req);

        assertNotNull(result);
        assertEquals("validated", result.getName());
    }

    @Test
    void updateParameter_notFound_throws() {
        when(parameterRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateParameterRequest req = new UpdateParameterRequest(
                null, null, null, null, null, null, null,
                null, null, null, 0);

        assertThrows(ResourceNotFoundException.class,
                () -> parameterService.updateParameter(99L, req));
    }


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

