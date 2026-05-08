package com.docgen.dto;

import java.util.List;
import java.util.Map;

/**
 * Describes the parameter schema for a template, including the tree structure,
 * metadata, and a sample request body.
 */
public record ParameterSchemaDTO(
        String templateName,
        int templateVersion,
        int totalParameterCount,
        int requiredParameterCount,
        List<ParameterSchemaEntry> parameters,
        Map<String, Object> sampleRequestBody
) {}
