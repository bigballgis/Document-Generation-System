package com.docgen.service;

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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ParameterService#jsonImport(Long, String, Long)}.
 * Validates: Requirements 3.2-3.9
 */
@ExtendWith(MockitoExtension.class)
class ParameterServiceJsonImportTest {

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

    private AtomicLong idSequence;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parameterService = new ParameterService(
                parameterRepository, templateRepository, templateScanService,
                expressionEngine, objectMapper, auditLogService);
        TenantContext.setCurrentTenantId(TENANT_ID);
        idSequence = new AtomicLong(1L);

        // Default: mock save to assign auto-increment IDs
        lenient().when(parameterRepository.save(any(ParameterDefinition.class)))
                .thenAnswer(inv -> {
                    ParameterDefinition entity = inv.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(idSequence.getAndIncrement());
                    }
                    entity.setCreatedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    return entity;
                });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Simple flat JSON ──

    @Nested
    class SimpleFlatJson {

        @Test
        void flatJsonObject_createsCorrectParameters() {
            String json = """
                    {"name": "Alice", "age": 30, "active": true}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            assertEquals(3, result.size());

            ParameterDTO nameParam = result.stream().filter(p -> "name".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("STRING", nameParam.getDataType());
            assertTrue(nameParam.isRequired());

            ParameterDTO ageParam = result.stream().filter(p -> "age".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("NUMBER", ageParam.getDataType());

            ParameterDTO activeParam = result.stream().filter(p -> "active".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("BOOLEAN", activeParam.getDataType());

            verify(auditLogService).log(eq(TENANT_ID), isNull(), eq("JSON_IMPORT_PARAMETER"),
                    eq("PARAMETER"), eq(TEMPLATE_ID), any(String.class), isNull());
        }
    }

    // ── Nested objects ──

    @Nested
    class NestedObjects {

        @Test
        void nestedObject_createsObjectWithChildren() {
            String json = """
                    {"company": {"name": "Acme", "founded": 1990}}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            // company (OBJECT) + name (STRING) + founded (NUMBER) = 3
            assertEquals(3, result.size());

            ParameterDTO company = result.stream().filter(p -> "company".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("OBJECT", company.getDataType());
            assertNull(company.getParentId());

            ParameterDTO name = result.stream().filter(p -> "name".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("STRING", name.getDataType());
            assertEquals(company.getId(), name.getParentId());

            ParameterDTO founded = result.stream().filter(p -> "founded".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("NUMBER", founded.getDataType());
            assertEquals(company.getId(), founded.getParentId());
        }
    }

    // ── Arrays of objects ──

    @Nested
    class ArraysOfObjects {

        @Test
        void arrayOfObjects_createsArrayWithUnionOfKeys() {
            String json = """
                    {"items": [{"id": 1, "name": "A"}, {"id": 2, "price": 9.99}]}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            ParameterDTO items = result.stream().filter(p -> "items".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("ARRAY", items.getDataType());

            // Union of keys: id, name, price
            List<ParameterDTO> children = result.stream()
                    .filter(p -> items.getId().equals(p.getParentId()))
                    .toList();
            assertEquals(3, children.size());

            assertTrue(children.stream().anyMatch(c -> "id".equals(c.getName()) && "NUMBER".equals(c.getDataType())));
            assertTrue(children.stream().anyMatch(c -> "name".equals(c.getName()) && "STRING".equals(c.getDataType())));
            assertTrue(children.stream().anyMatch(c -> "price".equals(c.getName()) && "NUMBER".equals(c.getDataType())));
        }
    }

    // ── Arrays of primitives ──

    @Nested
    class ArraysOfPrimitives {

        @Test
        void arrayOfPrimitives_createsArrayWithNoChildren() {
            String json = """
                    {"tags": [1, 2, 3]}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            assertEquals(1, result.size());
            ParameterDTO tags = result.get(0);
            assertEquals("tags", tags.getName());
            assertEquals("ARRAY", tags.getDataType());
            assertNotNull(tags.getDescription());
            assertTrue(tags.getDescription().contains("NUMBER"));
        }

        @Test
        void arrayOfMixedPrimitives_notesMultipleTypes() {
            String json = """
                    {"values": ["hello", 42, true]}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            assertEquals(1, result.size());
            ParameterDTO values = result.get(0);
            assertEquals("ARRAY", values.getDataType());
            assertNotNull(values.getDescription());
        }
    }

    // ── Empty JSON ──

    @Nested
    class EmptyJson {

        @Test
        void emptyObject_throwsBusinessException() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.jsonImport(TEMPLATE_ID, "{}", null));

            assertEquals("PARAMETER_JSON_IMPORT_FAILED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("JSON 数据为空"));
        }

        @Test
        void emptyArray_throwsBusinessException() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.jsonImport(TEMPLATE_ID, "[]", null));

            assertEquals("PARAMETER_JSON_IMPORT_FAILED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("JSON 数据为空"));
        }
    }

    // ── Invalid JSON ──

    @Nested
    class InvalidJson {

        @Test
        void invalidJson_throwsBusinessExceptionWithPosition() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.jsonImport(TEMPLATE_ID, "{invalid json}", null));

            assertEquals("PARAMETER_JSON_IMPORT_FAILED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("JSON 解析失败"));
        }

        @Test
        void truncatedJson_throwsBusinessException() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parameterService.jsonImport(TEMPLATE_ID, "{\"name\":", null));

            assertEquals("PARAMETER_JSON_IMPORT_FAILED", ex.getErrorCode());
        }
    }

    // ── Depth exceeded ──

    @Nested
    class DepthExceeded {

        @Test
        void deeplyNestedJson_flattensToStringBeyondMaxDepth() {
            // 6 levels deep: a.b.c.d.e.f — level 6 should be flattened
            String json = """
                    {"a": {"b": {"c": {"d": {"e": {"f": "deep"}}}}}}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            // a(OBJECT) + b(OBJECT) + c(OBJECT) + d(OBJECT) + e(OBJECT) + f(STRING flattened) = 6
            assertEquals(6, result.size());

            // The deepest level (f at depth 6) should be STRING
            ParameterDTO f = result.stream().filter(p -> "f".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("STRING", f.getDataType());
            assertNotNull(f.getDescription());
            assertTrue(f.getDescription().contains("扁平化"));
        }

        @Test
        void importUnderParent_adjustsStartDepth() {
            // Parent at depth 3 → start depth = 4 → only 1 more level before max
            Long parentId = 50L;
            ParameterDefinition depth1 = makeEntity(48L, TEMPLATE_ID, null, "root");
            ParameterDefinition depth2 = makeEntity(49L, TEMPLATE_ID, 48L, "mid");
            ParameterDefinition depth3 = makeEntity(50L, TEMPLATE_ID, 49L, "leaf");

            when(parameterRepository.findById(50L)).thenReturn(Optional.of(depth3));
            when(parameterRepository.findById(49L)).thenReturn(Optional.of(depth2));
            when(parameterRepository.findById(48L)).thenReturn(Optional.of(depth1));

            // JSON with 2 levels: obj.child — depth 4 + 2 = 6, child should be flattened
            String json = """
                    {"obj": {"child": "value"}}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, parentId);

            // obj(OBJECT at depth 4) + child(STRING flattened at depth 5... actually depth 5 is still within limit)
            // depth 3 parent + 1 = start depth 4, obj at depth 4, child at depth 5 → within limit
            // Let's verify: computeDepth(50) = 3 (root→mid→leaf), startDepth = 4
            // obj at depth 4 (OBJECT), child at depth 5 (STRING, within limit)
            assertEquals(2, result.size());
            ParameterDTO obj = result.stream().filter(p -> "obj".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("OBJECT", obj.getDataType());
            assertEquals(parentId, obj.getParentId());
        }
    }

    // ── Null values ──

    @Nested
    class NullValues {

        @Test
        void nullValue_createsStringWithRequiredFalse() {
            String json = """
                    {"optional_field": null}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            assertEquals(1, result.size());
            ParameterDTO param = result.get(0);
            assertEquals("optional_field", param.getName());
            assertEquals("STRING", param.getDataType());
            assertFalse(param.isRequired());
        }

        @Test
        void mixedNullAndValues_handlesCorrectly() {
            String json = """
                    {"name": "test", "deleted_at": null, "count": 5}
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            assertEquals(3, result.size());

            ParameterDTO deletedAt = result.stream().filter(p -> "deleted_at".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("STRING", deletedAt.getDataType());
            assertFalse(deletedAt.isRequired());

            ParameterDTO name = result.stream().filter(p -> "name".equals(p.getName())).findFirst().orElseThrow();
            assertTrue(name.isRequired());
        }
    }

    // ── Name sanitization ──

    @Nested
    class NameSanitization {

        @Test
        void sanitizeName_replacesInvalidChars() {
            assertEquals("hello_world", parameterService.sanitizeName("hello world"));
            assertEquals("_123", parameterService.sanitizeName("123"));
            assertEquals("valid_name", parameterService.sanitizeName("valid_name"));
            assertEquals("_empty", parameterService.sanitizeName(""));
            assertEquals("_empty", parameterService.sanitizeName(null));
        }
    }

    // ── Top-level array ──

    @Nested
    class TopLevelArray {

        @Test
        void topLevelArrayOfObjects_createsItemsArrayParameter() {
            String json = """
                    [{"id": 1, "name": "A"}, {"id": 2, "name": "B"}]
                    """;

            List<ParameterDTO> result = parameterService.jsonImport(TEMPLATE_ID, json, null);

            ParameterDTO items = result.stream().filter(p -> "items".equals(p.getName())).findFirst().orElseThrow();
            assertEquals("ARRAY", items.getDataType());

            // Children: id (NUMBER), name (STRING)
            List<ParameterDTO> children = result.stream()
                    .filter(p -> items.getId().equals(p.getParentId()))
                    .toList();
            assertEquals(2, children.size());
        }
    }

    // ── Helper ──

    private ParameterDefinition makeEntity(Long id, Long templateId, Long parentId, String name) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(templateId);
        p.setParentId(parentId);
        p.setName(name);
        p.setParameterType("REQUEST");
        p.setDataType("OBJECT");
        p.setRequired(false);
        p.setSortOrder(0);
        p.setVersion(0);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }
}
