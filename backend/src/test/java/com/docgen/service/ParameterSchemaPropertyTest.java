package com.docgen.service;

import com.docgen.dto.ParameterSchemaDTO;
import com.docgen.dto.ParameterSchemaEntry;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Parameter Schema generation in {@link ParameterService}.
 *
 * <p><b>Validates: Requirements 7.1, 7.2, 7.4, 7.5, 7.6, 7.7</b></p>
 */
@Tag("Feature: template-parameter-redesign")
class ParameterSchemaPropertyTest {

    private static final Long TEMPLATE_ID = 1L;

    private ParameterService createService(List<ParameterDefinition> params) {
        ParameterRepository paramRepo = mock(ParameterRepository.class);
        TemplateRepository templateRepo = mock(TemplateRepository.class);
        TemplateScanService scanService = mock(TemplateScanService.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        ObjectMapper mapper = new ObjectMapper();

        Template template = new Template();
        template.setId(TEMPLATE_ID);
        template.setName("Test Template");
        template.setTenantId(1L);

        when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(paramRepo.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID)).thenReturn(params);

        return new ParameterService(paramRepo, templateRepo, scanService, engine, mapper, mock(AuditLogService.class));
    }

    private ParameterDefinition makeParam(Long id, String name, String dataType,
                                           boolean required, String defaultValue) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(TEMPLATE_ID);
        p.setName(name);
        p.setParameterType("REQUEST");
        p.setDataType(dataType);
        p.setRequired(required);
        p.setDefaultValue(defaultValue);
        p.setSortOrder(id.intValue());
        return p;
    }

    private ParameterDefinition makeChild(Long id, Long parentId, String name, String dataType,
                                           boolean required) {
        ParameterDefinition p = makeParam(id, name, dataType, required, null);
        p.setParentId(parentId);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 24: Parameter schema structure
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 24: OBJECT parameters have children under "properties" key,
     * ARRAY parameters have children under "items" key.
     *
     * <p><b>Validates: Requirements 7.1, 7.5, 7.6, 7.7</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 24: Parameter schema structure")
    void objectParams_haveProperties_arrayParams_haveItems(
            @ForAll @IntRange(min = 1, max = 3) int numObjectChildren,
            @ForAll @IntRange(min = 1, max = 3) int numArrayChildren
    ) {
        List<ParameterDefinition> params = new ArrayList<>();

        // OBJECT parent with children
        ParameterDefinition objParent = makeParam(1L, "company", "OBJECT", false, null);
        params.add(objParent);
        for (int i = 0; i < numObjectChildren; i++) {
            params.add(makeChild((long) (10 + i), 1L, "field_" + i, "STRING", false));
        }

        // ARRAY parent with children
        ParameterDefinition arrParent = makeParam(2L, "items", "ARRAY", false, null);
        params.add(arrParent);
        for (int i = 0; i < numArrayChildren; i++) {
            params.add(makeChild((long) (20 + i), 2L, "item_field_" + i, "STRING", false));
        }

        ParameterService svc = createService(params);
        ParameterSchemaDTO schema = svc.getParameterSchema(TEMPLATE_ID);

        // Find OBJECT entry
        ParameterSchemaEntry objEntry = schema.parameters().stream()
                .filter(e -> "company".equals(e.name())).findFirst().orElseThrow();
        assertNotNull(objEntry.properties(), "OBJECT should have properties");
        assertNull(objEntry.items(), "OBJECT should not have items");
        assertEquals(numObjectChildren, objEntry.properties().size());

        // Find ARRAY entry
        ParameterSchemaEntry arrEntry = schema.parameters().stream()
                .filter(e -> "items".equals(e.name())).findFirst().orElseThrow();
        assertNotNull(arrEntry.items(), "ARRAY should have items");
        assertNull(arrEntry.properties(), "ARRAY should not have properties");
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 25: Parameter schema metadata accuracy
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 25: totalParameterCount = total REQUEST params (including nested),
     * requiredParameterCount = count of required REQUEST params.
     *
     * <p><b>Validates: Requirements 7.4</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 25: Parameter schema metadata accuracy")
    void schemaMetadata_countsAreAccurate(
            @ForAll @IntRange(min = 1, max = 8) int totalParams,
            @ForAll @IntRange(min = 0, max = 8) int requiredCount
    ) {
        int actualRequired = Math.min(requiredCount, totalParams);
        List<ParameterDefinition> params = new ArrayList<>();
        for (int i = 0; i < totalParams; i++) {
            params.add(makeParam((long) (i + 1), "p_" + i, "STRING", i < actualRequired, null));
        }

        ParameterService svc = createService(params);
        ParameterSchemaDTO schema = svc.getParameterSchema(TEMPLATE_ID);

        assertEquals(totalParams, schema.totalParameterCount(),
                "totalParameterCount should equal total REQUEST params");
        assertEquals(actualRequired, schema.requiredParameterCount(),
                "requiredParameterCount should equal count of required params");
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 26: Sample request body generation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 26: Sample request body contains all parameters in correct nested JSON structure
     * with example values based on data_type (using default_value when defined).
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 26: Sample request body generation")
    void sampleRequestBody_containsAllParams_withCorrectTypes(
            @ForAll @IntRange(min = 1, max = 5) int numParams
    ) {
        List<ParameterDefinition> params = new ArrayList<>();
        for (int i = 0; i < numParams; i++) {
            params.add(makeParam((long) (i + 1), "param_" + i, "STRING", false, null));
        }

        ParameterService svc = createService(params);
        ParameterSchemaDTO schema = svc.getParameterSchema(TEMPLATE_ID);

        assertNotNull(schema.sampleRequestBody());
        assertEquals(numParams, schema.sampleRequestBody().size(),
                "Sample body should contain all root params");

        for (int i = 0; i < numParams; i++) {
            String key = "param_" + i;
            assertTrue(schema.sampleRequestBody().containsKey(key),
                    "Sample body should contain key: " + key);
            assertInstanceOf(String.class, schema.sampleRequestBody().get(key),
                    "STRING param should have String example value");
        }
    }

    /**
     * Property 26: Nested OBJECT/ARRAY structures in sample body.
     */
    @Property(tries = 100)
    @Tag("Property 26: Sample request body generation")
    void sampleRequestBody_nestedStructures(
            @ForAll @IntRange(min = 1, max = 3) int numChildren
    ) {
        List<ParameterDefinition> params = new ArrayList<>();
        ParameterDefinition obj = makeParam(1L, "company", "OBJECT", false, null);
        params.add(obj);
        for (int i = 0; i < numChildren; i++) {
            params.add(makeChild((long) (10 + i), 1L, "field_" + i, "STRING", false));
        }

        ParameterService svc = createService(params);
        ParameterSchemaDTO schema = svc.getParameterSchema(TEMPLATE_ID);

        Object companyVal = schema.sampleRequestBody().get("company");
        assertInstanceOf(Map.class, companyVal, "OBJECT param should produce Map in sample");
        @SuppressWarnings("unchecked")
        Map<String, Object> companyMap = (Map<String, Object>) companyVal;
        assertEquals(numChildren, companyMap.size(),
                "OBJECT sample should contain all children");
    }

    /**
     * Property 26: Default values used in sample body.
     */
    @Property(tries = 100)
    @Tag("Property 26: Sample request body generation")
    void sampleRequestBody_usesDefaultValues(
            @ForAll("validParamNames") String paramName,
            @ForAll("nonEmptyStrings") String defaultVal
    ) {
        ParameterDefinition p = makeParam(1L, paramName, "STRING", false, defaultVal);
        ParameterService svc = createService(List.of(p));
        ParameterSchemaDTO schema = svc.getParameterSchema(TEMPLATE_ID);

        assertEquals(defaultVal, schema.sampleRequestBody().get(paramName),
                "Sample body should use default_value when defined");
    }

    // ── Generators ──

    @Provide
    Arbitrary<String> validParamNames() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz")
                .ofMinLength(2).ofMaxLength(10)
                .map(s -> "p" + s);
    }

    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings().ascii()
                .ofMinLength(1).ofMaxLength(20)
                .filter(s -> !s.contains("\"") && !s.contains("\\") && !s.contains("\n") && !s.isBlank());
    }
}
