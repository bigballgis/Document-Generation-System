package com.docgen.dto;

import java.util.List;

/**
 * Describes the aggregation schema for a single ARRAY parameter,
 * including the array name, path, and all available aggregation properties.
 */
public record AggregationSchemaDTO(
        String arrayName,
        String arrayPath,
        List<AggregationPropertyDTO> properties
) {}
