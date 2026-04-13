package com.docgen.util;

import com.docgen.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ParameterResolverTest {

    // ── resolve ──

    @Test
    void resolve_nullDefs_returnsRuntimeParams() {
        Map<String, Object> params = Map.of("key", "value");
        Map<String, Object> result = ParameterResolver.resolve(null, params);
        assertEquals("value", result.get("key"));
    }

    @Test
    void resolve_emptyDefs_returnsRuntimeParams() {
        Map<String, Object> params = Map.of("key", "value");
        Map<String, Object> result = ParameterResolver.resolve(List.of(), params);
        assertEquals("value", result.get("key"));
    }

    @Test
    void resolve_nullRuntimeParams_appliesDefaults() {
        List<Map<String, Object>> defs = List.of(
                Map.of("name", "page", "required", false, "defaultValue", "1")
        );
        Map<String, Object> result = ParameterResolver.resolve(defs, null);
        assertEquals("1", result.get("page"));
    }

    @Test
    void resolve_appliesDefaultForMissingOptional() {
        List<Map<String, Object>> defs = List.of(
                Map.of("name", "page", "required", false, "defaultValue", "1"),
                Map.of("name", "size", "required", false, "defaultValue", "20")
        );
        Map<String, Object> result = ParameterResolver.resolve(defs, Map.of("page", "3"));
        assertEquals("3", result.get("page"));
        assertEquals("20", result.get("size"));
    }

    @Test
    void resolve_runtimeOverridesDefault() {
        List<Map<String, Object>> defs = List.of(
                Map.of("name", "limit", "required", false, "defaultValue", "10")
        );
        Map<String, Object> result = ParameterResolver.resolve(defs, Map.of("limit", "50"));
        assertEquals("50", result.get("limit"));
    }

    @Test
    void resolve_requiredParamPresent_noError() {
        List<Map<String, Object>> defs = List.of(
                Map.of("name", "userId", "required", true)
        );
        Map<String, Object> result = ParameterResolver.resolve(defs, Map.of("userId", 42));
        assertEquals(42, result.get("userId"));
    }

    @Test
    void resolve_requiredParamWithDefault_usesDefault() {
        Map<String, Object> def = new HashMap<>();
        def.put("name", "userId");
        def.put("required", true);
        def.put("defaultValue", "99");
        List<Map<String, Object>> defs = List.of(def);
        Map<String, Object> result = ParameterResolver.resolve(defs, Map.of());
        assertEquals("99", result.get("userId"));
    }

    @Test
    void resolve_missingRequiredParam_throwsValidationException() {
        Map<String, Object> def = new HashMap<>();
        def.put("name", "userId");
        def.put("required", true);
        def.put("defaultValue", null);
        List<Map<String, Object>> defs = List.of(def);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> ParameterResolver.resolve(defs, Map.of()));
        assertTrue(ex.getMessage().contains("userId"));
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) ex.getDetails().get("missingParameters");
        assertTrue(missing.contains("userId"));
    }

    @Test
    void resolve_multipleMissingRequired_reportsAll() {
        Map<String, Object> def1 = new HashMap<>();
        def1.put("name", "userId");
        def1.put("required", true);
        def1.put("defaultValue", null);
        Map<String, Object> def2 = new HashMap<>();
        def2.put("name", "orgId");
        def2.put("required", true);
        def2.put("defaultValue", null);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> ParameterResolver.resolve(List.of(def1, def2), Map.of()));
        assertTrue(ex.getMessage().contains("userId"));
        assertTrue(ex.getMessage().contains("orgId"));
    }

    // ── replacePathParameters ──

    @Test
    void replacePathParameters_noPlaceholders() {
        String result = ParameterResolver.replacePathParameters(
                "http://api.example.com/users", Map.of("userId", "123"));
        assertEquals("http://api.example.com/users", result);
    }

    @Test
    void replacePathParameters_singleParam() {
        String result = ParameterResolver.replacePathParameters(
                "http://api.example.com/users/{userId}",
                Map.of("userId", "123"));
        assertEquals("http://api.example.com/users/123", result);
    }

    @Test
    void replacePathParameters_multipleParams() {
        String result = ParameterResolver.replacePathParameters(
                "/users/{userId}/posts/{postId}",
                Map.of("userId", "42", "postId", "7"));
        assertEquals("/users/42/posts/7", result);
    }

    @Test
    void replacePathParameters_urlEncodesValues() {
        String result = ParameterResolver.replacePathParameters(
                "/search/{query}",
                Map.of("query", "hello world"));
        assertEquals("/search/hello+world", result);
    }

    @Test
    void replacePathParameters_specialCharsEncoded() {
        String result = ParameterResolver.replacePathParameters(
                "/path/{name}",
                Map.of("name", "a/b&c=d"));
        assertEquals("/path/a%2Fb%26c%3Dd", result);
    }

    @Test
    void replacePathParameters_missingParam_leavesPlaceholder() {
        String result = ParameterResolver.replacePathParameters(
                "/users/{userId}", Map.of());
        assertEquals("/users/{userId}", result);
    }

    @Test
    void replacePathParameters_nullUrl_returnsNull() {
        assertNull(ParameterResolver.replacePathParameters(null, Map.of()));
    }

    @Test
    void replacePathParameters_nullParams_returnsOriginal() {
        assertEquals("/users/{userId}",
                ParameterResolver.replacePathParameters("/users/{userId}", null));
    }

    @Test
    void replacePathParameters_numericValue() {
        String result = ParameterResolver.replacePathParameters(
                "/users/{id}", Map.of("id", 42));
        assertEquals("/users/42", result);
    }

    // ── extractParameterDefs ──

    @Test
    void extractParameterDefs_noParametersKey_returnsEmpty() {
        List<Map<String, Object>> result = ParameterResolver.extractParameterDefs(Map.of("url", "http://x"));
        assertTrue(result.isEmpty());
    }

    @Test
    void extractParameterDefs_withDefs_returnsList() {
        Map<String, Object> config = Map.of(
                "parameters", List.of(
                        Map.of("name", "userId", "required", true),
                        Map.of("name", "page", "required", false, "defaultValue", "1")
                )
        );
        List<Map<String, Object>> result = ParameterResolver.extractParameterDefs(config);
        assertEquals(2, result.size());
        assertEquals("userId", result.get(0).get("name"));
    }

    @Test
    void extractParameterDefs_nonListValue_returnsEmpty() {
        Map<String, Object> config = Map.of("parameters", "not-a-list");
        List<Map<String, Object>> result = ParameterResolver.extractParameterDefs(config);
        assertTrue(result.isEmpty());
    }
}
