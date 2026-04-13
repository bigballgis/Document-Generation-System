package com.docgen.util;

import com.docgen.exception.ErrorCode;
import com.docgen.exception.ValidationException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for resolving parameterized data source queries.
 * <p>
 * Supports:
 * <ul>
 *   <li>Validating required parameters are present (or have defaults)</li>
 *   <li>Applying default values for missing optional parameters</li>
 *   <li>Replacing path parameters in URLs ({paramName} → URL-encoded value)</li>
 * </ul>
 */
public final class ParameterResolver {

    private ParameterResolver() {
    }

    /** Pattern matching {paramName} in URLs. */
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    /**
     * Resolve parameters by validating required ones and applying defaults.
     *
     * @param parameterDefs list of parameter definitions from configJson "parameters" section.
     *                      Each map should have: "name" (String), "required" (Boolean), "defaultValue" (String|null).
     * @param runtimeParams parameters provided at runtime
     * @return resolved parameter map with defaults applied
     * @throws ValidationException if required parameters are missing
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolve(List<Map<String, Object>> parameterDefs,
                                               Map<String, Object> runtimeParams) {
        if (parameterDefs == null || parameterDefs.isEmpty()) {
            return runtimeParams != null ? new LinkedHashMap<>(runtimeParams) : new LinkedHashMap<>();
        }

        Map<String, Object> resolved = runtimeParams != null
                ? new LinkedHashMap<>(runtimeParams)
                : new LinkedHashMap<>();

        List<String> missingParams = new ArrayList<>();

        for (Map<String, Object> def : parameterDefs) {
            String name = String.valueOf(def.get("name"));
            boolean required = Boolean.TRUE.equals(def.get("required"));
            Object defaultValue = def.get("defaultValue");

            if (!resolved.containsKey(name) || resolved.get(name) == null) {
                if (defaultValue != null) {
                    resolved.put(name, defaultValue);
                } else if (required) {
                    missingParams.add(name);
                }
            }
        }

        if (!missingParams.isEmpty()) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "缺少必填参数: " + String.join(", ", missingParams),
                    Map.of("missingParameters", missingParams)
            );
        }

        return resolved;
    }

    /**
     * Replace path parameters in a URL template.
     * E.g. "/users/{userId}/posts/{postId}" with {userId=123, postId=456}
     * becomes "/users/123/posts/456". Values are URL-encoded.
     *
     * @param urlTemplate the URL containing {paramName} placeholders
     * @param parameters  the parameter values
     * @return the URL with path parameters replaced
     */
    public static String replacePathParameters(String urlTemplate, Map<String, Object> parameters) {
        if (urlTemplate == null || parameters == null) {
            return urlTemplate;
        }

        Matcher matcher = PATH_PARAM_PATTERN.matcher(urlTemplate);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = parameters.get(paramName);
            String replacement = value != null
                    ? URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)
                    : matcher.group(0); // leave unreplaced if no value
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Extract parameter definitions from a parsed config map.
     *
     * @param config the parsed configJson map
     * @return list of parameter definition maps, or empty list if none
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractParameterDefs(Map<String, Object> config) {
        Object paramsDef = config.get("parameters");
        if (paramsDef instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
