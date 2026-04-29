package com.docgen.property;

import com.docgen.dto.TemplateConfigExport;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for template config import/export round-trip consistency.
 *
 * <p>Verifies that exporting a TemplateConfigExport to JSON and parsing it back
 * produces an equivalent configuration object.</p>
 *
 * <p><b>Validates: Requirements 30.4, 30.5, 30.6</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 9: 模板配置导入导出往返一致性")
class TemplateConfigRoundTripPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 9: Template config import/export round-trip consistency.
     *
     * For any valid TemplateConfigExport, serializing to JSON bytes then
     * deserializing back should produce an equivalent configuration.
     */
    @Property(tries = 200)
    void configExportThenImportShouldPreserveAllFields(
            @ForAll("validTemplateConfigs") TemplateConfigExport original
    ) throws Exception {
        // Export: serialize to JSON bytes
        byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(original);

        // Import: deserialize from JSON bytes
        TemplateConfigExport restored = objectMapper.readValue(jsonBytes, TemplateConfigExport.class);

        // Verify template metadata
        assertTemplateMetadataEqual(original.getTemplate(), restored.getTemplate());

        // Verify data sources
        assertDataSourcesEqual(original.getDataSources(), restored.getDataSources());

        // Verify expressions
        assertExpressionsEqual(original.getExpressions(), restored.getExpressions());

        // Verify variables
        assertVariablesEqual(original.getVariables(), restored.getVariables());

        // Verify version field
        assertEquals(original.getVersion(), restored.getVersion(),
                "Version should be preserved");
    }


    private void assertTemplateMetadataEqual(
            TemplateConfigExport.TemplateMetadata expected,
            TemplateConfigExport.TemplateMetadata actual) {
        assertNotNull(actual, "Template metadata should not be null after round-trip");
        assertEquals(expected.getName(), actual.getName(), "Template name should be preserved");
        assertEquals(expected.getDescription(), actual.getDescription(), "Template description should be preserved");
        assertEquals(expected.getOutputFormat(), actual.getOutputFormat(), "OutputFormat should be preserved");
        assertEquals(expected.getStorageStrategy(), actual.getStorageStrategy(), "StorageStrategy should be preserved");
        assertEquals(expected.isAsync(), actual.isAsync(), "Async flag should be preserved");
        assertEquals(expected.isReviewRequired(), actual.isReviewRequired(), "ReviewRequired flag should be preserved");
    }

    private void assertDataSourcesEqual(
            List<TemplateConfigExport.DataSourceExport> expected,
            List<TemplateConfigExport.DataSourceExport> actual) {
        if (expected == null) {
            assertNull(actual, "DataSources should be null when original is null");
            return;
        }
        assertNotNull(actual, "DataSources should not be null after round-trip");
        assertEquals(expected.size(), actual.size(), "DataSources count should be preserved");
        for (int i = 0; i < expected.size(); i++) {
            TemplateConfigExport.DataSourceExport e = expected.get(i);
            TemplateConfigExport.DataSourceExport a = actual.get(i);
            assertEquals(e.getName(), a.getName(), "DataSource[" + i + "].name should be preserved");
            assertEquals(e.getType(), a.getType(), "DataSource[" + i + "].type should be preserved");
            assertEquals(e.getConfigJson(), a.getConfigJson(), "DataSource[" + i + "].configJson should be preserved");
            assertEquals(e.isCacheEnabled(), a.isCacheEnabled(), "DataSource[" + i + "].cacheEnabled should be preserved");
            assertEquals(e.getCacheTtl(), a.getCacheTtl(), "DataSource[" + i + "].cacheTtl should be preserved");
            assertEquals(e.getPriority(), a.getPriority(), "DataSource[" + i + "].priority should be preserved");
        }
    }

    private void assertExpressionsEqual(
            List<TemplateConfigExport.ExpressionExport> expected,
            List<TemplateConfigExport.ExpressionExport> actual) {
        if (expected == null) {
            assertNull(actual, "Expressions should be null when original is null");
            return;
        }
        assertNotNull(actual, "Expressions should not be null after round-trip");
        assertEquals(expected.size(), actual.size(), "Expressions count should be preserved");
        for (int i = 0; i < expected.size(); i++) {
            TemplateConfigExport.ExpressionExport e = expected.get(i);
            TemplateConfigExport.ExpressionExport a = actual.get(i);
            assertEquals(e.getName(), a.getName(), "Expression[" + i + "].name should be preserved");
            assertEquals(e.getExpressionType(), a.getExpressionType(), "Expression[" + i + "].expressionType should be preserved");
            assertEquals(e.getExpressionText(), a.getExpressionText(), "Expression[" + i + "].expressionText should be preserved");
            assertEquals(e.getDescription(), a.getDescription(), "Expression[" + i + "].description should be preserved");
            assertEquals(e.getExecutionOrder(), a.getExecutionOrder(), "Expression[" + i + "].executionOrder should be preserved");
        }
    }

    private void assertVariablesEqual(
            List<TemplateConfigExport.VariableExport> expected,
            List<TemplateConfigExport.VariableExport> actual) {
        if (expected == null) {
            assertNull(actual, "Variables should be null when original is null");
            return;
        }
        assertNotNull(actual, "Variables should not be null after round-trip");
        assertEquals(expected.size(), actual.size(), "Variables count should be preserved");
        for (int i = 0; i < expected.size(); i++) {
            TemplateConfigExport.VariableExport e = expected.get(i);
            TemplateConfigExport.VariableExport a = actual.get(i);
            assertEquals(e.getName(), a.getName(), "Variable[" + i + "].name should be preserved");
            assertEquals(e.getVariableType(), a.getVariableType(), "Variable[" + i + "].variableType should be preserved");
            assertEquals(e.getDefaultValue(), a.getDefaultValue(), "Variable[" + i + "].defaultValue should be preserved");
            assertEquals(e.getDescription(), a.getDescription(), "Variable[" + i + "].description should be preserved");
            assertEquals(e.getBindingSource(), a.getBindingSource(), "Variable[" + i + "].bindingSource should be preserved");
            assertEquals(e.getBindingField(), a.getBindingField(), "Variable[" + i + "].bindingField should be preserved");
            assertEquals(e.isBound(), a.isBound(), "Variable[" + i + "].bound should be preserved");
        }
    }


    @Provide
    Arbitrary<TemplateConfigExport> validTemplateConfigs() {
        return Combinators.combine(
                templateMetadataArbitrary(),
                dataSourceListArbitrary(),
                expressionListArbitrary(),
                variableListArbitrary()
        ).as((metadata, dataSources, expressions, variables) -> {
            TemplateConfigExport config = new TemplateConfigExport();
            config.setVersion("1.0");
            config.setTemplate(metadata);
            config.setDataSources(dataSources);
            config.setExpressions(expressions);
            config.setVariables(variables);
            return config;
        });
    }

    private Arbitrary<TemplateConfigExport.TemplateMetadata> templateMetadataArbitrary() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().numeric().withChars(' ', '-', '_')
                .ofMinLength(1).ofMaxLength(50)
                .filter(s -> !s.isBlank());

        Arbitrary<String> descriptions = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                        .ofMinLength(0).ofMaxLength(200)
        );

        Arbitrary<String> outputFormats = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of("WORD", "PDF", "BOTH")
        );

        Arbitrary<String> storageStrategies = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of("TEMP", "PERSISTENT")
        );

        Arbitrary<Boolean> booleans = Arbitraries.of(true, false);

        return Combinators.combine(names, descriptions, outputFormats, storageStrategies, booleans, booleans)
                .as((name, desc, format, strategy, async, review) -> {
                    TemplateConfigExport.TemplateMetadata m = new TemplateConfigExport.TemplateMetadata();
                    m.setName(name);
                    m.setDescription(desc);
                    m.setOutputFormat(format);
                    m.setStorageStrategy(strategy);
                    m.setAsync(async);
                    m.setReviewRequired(review);
                    return m;
                });
    }

    private Arbitrary<List<TemplateConfigExport.DataSourceExport>> dataSourceListArbitrary() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                dataSourceArbitrary().list().ofMinSize(0).ofMaxSize(5)
        );
    }

    private Arbitrary<TemplateConfigExport.DataSourceExport> dataSourceArbitrary() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().numeric().withChars('_', '-')
                .ofMinLength(1).ofMaxLength(30)
                .filter(s -> !s.isBlank());

        Arbitrary<String> types = Arbitraries.of("HTTP_API", "DATABASE", "INTERNAL_SYSTEM");

        Arbitrary<String> configJsons = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just("{}"),
                Arbitraries.just("{\"url\":\"https://api.example.com\"}"),
                Arbitraries.just("{\"host\":\"db.local\",\"port\":5432}")
        );

        Arbitrary<Boolean> cacheEnabled = Arbitraries.of(true, false);
        Arbitrary<Integer> cacheTtls = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.integers().between(60, 86400).map(i -> i)
        );
        Arbitrary<Integer> priorities = Arbitraries.integers().between(0, 100);

        return Combinators.combine(names, types, configJsons, cacheEnabled, cacheTtls, priorities)
                .as((name, type, configJson, cache, ttl, priority) -> {
                    TemplateConfigExport.DataSourceExport ds = new TemplateConfigExport.DataSourceExport();
                    ds.setName(name);
                    ds.setType(type);
                    ds.setConfigJson(configJson);
                    ds.setCacheEnabled(cache);
                    ds.setCacheTtl(ttl);
                    ds.setPriority(priority);
                    return ds;
                });
    }

    private Arbitrary<List<TemplateConfigExport.ExpressionExport>> expressionListArbitrary() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                expressionArbitrary().list().ofMinSize(0).ofMaxSize(5)
        );
    }

    private Arbitrary<TemplateConfigExport.ExpressionExport> expressionArbitrary() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().numeric().withChars('_')
                .ofMinLength(1).ofMaxLength(30)
                .filter(s -> !s.isBlank());

        Arbitrary<String> exprTypes = Arbitraries.of("JAVASCRIPT", "EXCEL_FORMULA");

        Arbitrary<String> exprTexts = Arbitraries.oneOf(
                Arbitraries.just("data.price * data.quantity"),
                Arbitraries.just("SUM(A1:A10)"),
                Arbitraries.just("IF(data.total > 1000, 'VIP', 'Normal')"),
                Arbitraries.strings().alpha().numeric().withChars('.', '+', '-', '*', '/', '(', ')', ' ')
                        .ofMinLength(1).ofMaxLength(100)
                        .filter(s -> !s.isBlank())
        );

        Arbitrary<String> descriptions = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().numeric().withChars(' ')
                        .ofMinLength(1).ofMaxLength(100)
        );

        Arbitrary<Integer> executionOrders = Arbitraries.integers().between(0, 100);

        return Combinators.combine(names, exprTypes, exprTexts, descriptions, executionOrders)
                .as((name, type, text, desc, order) -> {
                    TemplateConfigExport.ExpressionExport expr = new TemplateConfigExport.ExpressionExport();
                    expr.setName(name);
                    expr.setExpressionType(type);
                    expr.setExpressionText(text);
                    expr.setDescription(desc);
                    expr.setExecutionOrder(order);
                    return expr;
                });
    }

    private Arbitrary<List<TemplateConfigExport.VariableExport>> variableListArbitrary() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                variableArbitrary().list().ofMinSize(0).ofMaxSize(8)
        );
    }

    private Arbitrary<TemplateConfigExport.VariableExport> variableArbitrary() {
        Arbitrary<String> names = Arbitraries.strings()
                .alpha().numeric().withChars('_')
                .ofMinLength(1).ofMaxLength(30)
                .filter(s -> !s.isBlank());

        Arbitrary<String> varTypes = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of("STRING", "NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
        );

        Arbitrary<String> defaultValues = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("default"),
                Arbitraries.just("0"),
                Arbitraries.just("true"),
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(50)
        );

        Arbitrary<String> descriptions = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().numeric().withChars(' ')
                        .ofMinLength(1).ofMaxLength(100)
        );

        Arbitrary<String> bindingSources = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just("userApi"),
                Arbitraries.just("orderDb"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
        );

        Arbitrary<String> bindingFields = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just("name"),
                Arbitraries.just("data.items[0].price"),
                Arbitraries.strings().alpha().numeric().withChars('.', '[', ']', '_')
                        .ofMinLength(1).ofMaxLength(50)
        );

        Arbitrary<Boolean> boundFlags = Arbitraries.of(true, false);

        return Combinators.combine(names, varTypes, defaultValues, descriptions, bindingSources, bindingFields, boundFlags)
                .as((name, varType, defVal, desc, bindSrc, bindField, bound) -> {
                    TemplateConfigExport.VariableExport v = new TemplateConfigExport.VariableExport();
                    v.setName(name);
                    v.setVariableType(varType);
                    v.setDefaultValue(defVal);
                    v.setDescription(desc);
                    v.setBindingSource(bindSrc);
                    v.setBindingField(bindField);
                    v.setBound(bound);
                    return v;
                });
    }
}

