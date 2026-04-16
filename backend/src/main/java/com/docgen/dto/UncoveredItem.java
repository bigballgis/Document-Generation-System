package com.docgen.dto;

/**
 * Represents a single uncovered item in a coverage report.
 *
 * @param type        the coverage dimension: BRANCH, LOOP, or PARAMETER
 * @param name        the condition name, loop variable name, or parameter name
 * @param missingPath what is missing: "true"/"false" for branches, "empty"/"non-empty" for loops, "null" for parameters
 */
public record UncoveredItem(
        String type,
        String name,
        String missingPath
) {}
