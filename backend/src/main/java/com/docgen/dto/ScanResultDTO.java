package com.docgen.dto;

import java.util.List;

/**
 * Result of scanning a template for placeholders and comparing
 * them against existing parameter definitions.
 */
public record ScanResultDTO(
        List<PlaceholderInfo> matched,
        List<PlaceholderInfo> unmatchedPlaceholders,
        List<ParameterDTO> unusedParameters
) {}
