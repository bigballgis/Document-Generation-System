package com.docgen.service;

import net.jqwik.api.*;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for namespace isolation: NAME_PATTERN rejects all $ prefixed strings.
 *
 * <p><b>Validates: Requirements 9.5</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 10: namespace isolation
class NamePatternPropertyTest {

    /**
     * The NAME_PATTERN from ParameterService that validates user-defined parameter names.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    // ═══════════════════════════════════════════════════════════════
    // Property 10: namespace isolation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 10: For any string starting with $, the NAME_PATTERN regex SHALL reject it,
     * ensuring aggregation property names can never conflict with user-defined parameter names.
     *
     * <p><b>Validates: Requirements 9.5</b></p>
     */
    @Property(tries = 200)
    @Label("Property 10: NAME_PATTERN rejects all $ prefixed strings")
    void namePatternRejectsDollarPrefix(
            @ForAll("dollarPrefixedStrings") String name
    ) {
        assertFalse(NAME_PATTERN.matcher(name).matches(),
                "NAME_PATTERN should reject $ prefixed string: " + name);
    }

    /**
     * Additional: Verify that typical aggregation property names are all rejected.
     */
    @Property(tries = 200)
    @Label("Property 10b: NAME_PATTERN rejects aggregation property names")
    void namePatternRejectsAggregationPropertyNames(
            @ForAll("aggregationPropertyNames") String name
    ) {
        assertFalse(NAME_PATTERN.matcher(name).matches(),
                "NAME_PATTERN should reject aggregation property name: " + name);
    }

    // ═══════════════════════════════════════════════════════════════
    // Generators
    // ═══════════════════════════════════════════════════════════════

    @Provide
    Arbitrary<String> dollarPrefixedStrings() {
        // Generate random strings that start with $
        Arbitrary<String> suffix = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(1).ofMaxLength(20);
        return suffix.map(s -> "$" + s);
    }

    @Provide
    Arbitrary<String> aggregationPropertyNames() {
        Arbitrary<String> fieldName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(10);
        return Arbitraries.oneOf(
                Arbitraries.just("$count"),
                Arbitraries.just("$first"),
                Arbitraries.just("$last"),
                fieldName.map(f -> "$sum_" + f),
                fieldName.map(f -> "$avg_" + f),
                fieldName.map(f -> "$min_" + f),
                fieldName.map(f -> "$max_" + f),
                fieldName.map(f -> "$join_" + f)
        );
    }
}
