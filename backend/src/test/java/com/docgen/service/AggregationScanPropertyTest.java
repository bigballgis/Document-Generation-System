package com.docgen.service;

import com.docgen.dto.PlaceholderInfo;
import net.jqwik.api.*;

import java.util.List;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for aggregation placeholder scanning in {@link TemplateScanService}.
 *
 * <p><b>Validates: Requirements 8.1, 8.2, 8.3</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 9: 聚合占位符扫描正确性
class AggregationScanPropertyTest {

    private final TemplateScanService scanService;

    AggregationScanPropertyTest() {
        // TemplateScanService needs MinioClient but we only test parsing methods directly
        this.scanService = new TemplateScanService(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 9: 聚合占位符扫描正确性
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 9a: Any placeholder containing a $ prefix segment SHALL be classified as AGGREGATION.
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 200)
    @Label("Property 9a: $ prefix segments classified as AGGREGATION")
    void dollarPrefixClassifiedAsAggregation(
            @ForAll("aggregationPlaceholderXml") String xmlContent
    ) {
        List<PlaceholderInfo> result = scanService.parsePlaceholders(xmlContent);
        assertFalse(result.isEmpty(), "Should parse at least one placeholder");

        for (PlaceholderInfo ph : result) {
            boolean hasDollarSegment = ph.segments().stream().anyMatch(s -> s.startsWith("$"));
            if (hasDollarSegment) {
                assertEquals("AGGREGATION", ph.type(),
                        "Placeholder with $ segment should be AGGREGATION: " + ph.fullPath());
            }
        }
    }

    /**
     * Property 9b: Placeholders without $ prefix segments SHALL NOT be classified as AGGREGATION.
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 200)
    @Label("Property 9b: non-$ placeholders not classified as AGGREGATION")
    void nonDollarNotClassifiedAsAggregation(
            @ForAll("regularPlaceholderXml") String xmlContent
    ) {
        List<PlaceholderInfo> result = scanService.parsePlaceholders(xmlContent);
        assertFalse(result.isEmpty(), "Should parse at least one placeholder");

        for (PlaceholderInfo ph : result) {
            assertNotEquals("AGGREGATION", ph.type(),
                    "Placeholder without $ segment should not be AGGREGATION: " + ph.fullPath());
        }
    }

    /**
     * Property 9c: The PLACEHOLDER_PATTERN regex SHALL match placeholders with $ prefix segments.
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 200)
    @Label("Property 9c: regex matches $ prefix placeholders")
    void regexMatchesDollarPrefixPlaceholders(
            @ForAll("aggregationPlaceholderString") String placeholder
    ) {
        Matcher matcher = TemplateScanService.PLACEHOLDER_PATTERN.matcher(placeholder);
        assertTrue(matcher.find(), "Regex should match placeholder: " + placeholder);
    }

    // ═══════════════════════════════════════════════════════════════
    // Generators
    // ═══════════════════════════════════════════════════════════════

    @Provide
    Arbitrary<String> aggregationPlaceholderXml() {
        Arbitrary<String> arrayName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2).ofMaxLength(8);
        Arbitrary<String> aggFunc = Arbitraries.of(
                "$count", "$sum_price", "$avg_amount", "$min_qty", "$max_total",
                "$join_name", "$first", "$last", "$sum_x", "$avg_y"
        );
        return Combinators.combine(arrayName, aggFunc)
                .as((arr, func) -> "{" + arr + "." + func + "}");
    }

    @Provide
    Arbitrary<String> regularPlaceholderXml() {
        return Arbitraries.oneOf(
                // Simple placeholder
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(8)
                        .map(s -> "{" + s + "}"),
                // Dot-notation placeholder (no $ segments)
                Combinators.combine(
                        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(6),
                        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(6)
                ).as((a, b) -> "{" + a + "." + b + "}")
        );
    }

    @Provide
    Arbitrary<String> aggregationPlaceholderString() {
        Arbitrary<String> arrayName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2).ofMaxLength(8);
        Arbitrary<String> aggFunc = Arbitraries.of(
                "$count", "$sum_price", "$avg_amount", "$min_qty", "$max_total",
                "$join_name", "$first", "$last"
        );
        return Combinators.combine(arrayName, aggFunc)
                .as((arr, func) -> "{" + arr + "." + func + "}");
    }
}
