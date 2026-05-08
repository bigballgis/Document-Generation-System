package com.docgen.dto;

/**
 * A single aggregation property available for an ARRAY parameter.
 * Describes the property name, placeholder path, result data type, and a human-readable description.
 */
public record AggregationPropertyDTO(
        String name,
        String placeholderPath,
        String resultDataType,
        String description
) {}
