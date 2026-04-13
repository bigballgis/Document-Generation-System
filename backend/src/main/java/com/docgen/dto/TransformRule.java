package com.docgen.dto;

/**
 * Defines a single transformation rule applied to an API response.
 * <p>
 * Supported rule types:
 * <ul>
 *   <li>{@code JSONPATH} – extract data using a JSONPath expression</li>
 *   <li>{@code XPATH} – extract data from XML using an XPath expression</li>
 *   <li>{@code FLATTEN} – flatten nested objects to single-level key-value pairs</li>
 *   <li>{@code GROUP_BY} – group array elements by a specified field</li>
 *   <li>{@code SORT} – sort array elements by a specified field (ASC/DESC)</li>
 * </ul>
 *
 * Validates: Requirements 42.1-42.7
 */
public class TransformRule {

    public enum RuleType {
        JSONPATH, XPATH, FLATTEN, GROUP_BY, SORT
    }

    public enum SortDirection {
        ASC, DESC
    }

    private String name;
    private RuleType type;
    private String expression; // JSONPath/XPath expression, or field name for GROUP_BY/SORT
    private SortDirection direction; // only used for SORT

    public TransformRule() {
    }

    public TransformRule(String name, RuleType type, String expression) {
        this.name = name;
        this.type = type;
        this.expression = expression;
    }

    public TransformRule(String name, RuleType type, String expression, SortDirection direction) {
        this.name = name;
        this.type = type;
        this.expression = expression;
        this.direction = direction;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public RuleType getType() { return type; }
    public void setType(RuleType type) { this.type = type; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public SortDirection getDirection() { return direction; }
    public void setDirection(SortDirection direction) { this.direction = direction; }
}
