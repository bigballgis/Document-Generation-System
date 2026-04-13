package com.docgen.entity;

/**
 * Comparison strategy for template test cases.
 */
public enum ComparisonType {
    /** Compare rendered variable values against expected values. */
    VARIABLE_VALUE,
    /** Compare generated document plain-text content against expected text. */
    TEXT_CONTENT,
    /** Compare generated document binary hash against a baseline snapshot. */
    FILE_SNAPSHOT
}
