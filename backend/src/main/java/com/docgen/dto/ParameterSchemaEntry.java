package com.docgen.dto;

import java.util.List;
import java.util.Map;

/**
 * A single entry in the parameter schema tree.
 * Uses "properties" for OBJECT children and "items" for ARRAY children,
 * consistent with JSON Schema conventions.
 */
public record ParameterSchemaEntry(
        String name,
        String dataType,
        boolean required,
        String defaultValue,
        String description,
        Map<String, Object> validationRules,
        List<ParameterSchemaEntry> properties,
        ParameterSchemaEntry items
) {}
