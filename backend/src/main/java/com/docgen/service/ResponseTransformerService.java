package com.docgen.service;

import com.docgen.dto.TransformRule;
import com.docgen.dto.TransformRule.RuleType;
import com.docgen.dto.TransformRule.SortDirection;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Transforms API responses using configurable rules.
 * <p>
 * Supports JSONPath extraction, XPath extraction, data flattening,
 * array grouping, and array sorting.
 * <p>
 * On rule failure the result map includes an entry keyed by the rule name
 * whose value is a map with {@code _error} and {@code _ruleName} fields.
 * <p>
 * Validates: Requirements 42.1-42.7
 */
@Service
public class ResponseTransformerService {

    private static final Logger log = LoggerFactory.getLogger(ResponseTransformerService.class);

    /**
     * Apply a list of transformation rules to a raw response.
     *
     * @param rawResponse the raw API response (Map, List, or String for XML)
     * @param rules       the ordered list of transformation rules
     * @return a map keyed by rule name with the extracted/transformed value,
     *         or an error descriptor on failure
     */
    public Map<String, Object> transform(Object rawResponse, List<TransformRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (TransformRule rule : rules) {
            String ruleName = rule.getName() != null ? rule.getName() : "unnamed";
            try {
                Object value = applyRule(rawResponse, rule);
                result.put(ruleName, value);
            } catch (Exception e) {
                log.warn("Transform rule '{}' failed: {}", ruleName, e.getMessage());
                result.put(ruleName, errorEntry(ruleName, e.getMessage()));
            }
        }
        return result;
    }

    private Object applyRule(Object rawResponse, TransformRule rule) {
        return switch (rule.getType()) {
            case JSONPATH -> applyJsonPath(rawResponse, rule.getExpression());
            case XPATH -> applyXPath(rawResponse, rule.getExpression());
            case FLATTEN -> applyFlatten(rawResponse);
            case GROUP_BY -> applyGroupBy(rawResponse, rule.getExpression());
            case SORT -> applySort(rawResponse, rule.getExpression(), rule.getDirection());
        };
    }


    Object applyJsonPath(Object rawResponse, String expression) {
        return JsonPath.read(rawResponse, expression);
    }


    Object applyXPath(Object rawResponse, String expression) {
        try {
            String xml = rawResponse instanceof String s ? s : String.valueOf(rawResponse);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            XPath xpath = XPathFactory.newInstance().newXPath();
            // Try as node list first, fall back to string
            NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
            if (nodes.getLength() == 1) {
                return nodes.item(0).getTextContent();
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                values.add(nodes.item(i).getTextContent());
            }
            return values;
        } catch (Exception e) {
            throw new RuntimeException("XPath evaluation failed: " + e.getMessage(), e);
        }
    }


    @SuppressWarnings("unchecked")
    Object applyFlatten(Object rawResponse) {
        if (!(rawResponse instanceof Map)) {
            throw new IllegalArgumentException("FLATTEN requires a Map input");
        }
        Map<String, Object> source = (Map<String, Object>) rawResponse;
        Map<String, Object> flat = new LinkedHashMap<>();
        flattenRecursive("", source, flat);
        return flat;
    }

    @SuppressWarnings("unchecked")
    private void flattenRecursive(String prefix, Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenRecursive(key, (Map<String, Object>) value, target);
            } else {
                target.put(key, value);
            }
        }
    }


    @SuppressWarnings("unchecked")
    Object applyGroupBy(Object rawResponse, String field) {
        if (!(rawResponse instanceof List)) {
            throw new IllegalArgumentException("GROUP_BY requires a List input");
        }
        List<Object> list = (List<Object>) rawResponse;
        return list.stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .collect(Collectors.groupingBy(
                        item -> String.valueOf(item.getOrDefault(field, "null")),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }


    @SuppressWarnings("unchecked")
    Object applySort(Object rawResponse, String field, SortDirection direction) {
        if (!(rawResponse instanceof List)) {
            throw new IllegalArgumentException("SORT requires a List input");
        }
        SortDirection dir = direction != null ? direction : SortDirection.ASC;
        List<Object> list = new ArrayList<>((List<Object>) rawResponse);

        list.sort((a, b) -> {
            Object va = a instanceof Map ? ((Map<String, Object>) a).get(field) : null;
            Object vb = b instanceof Map ? ((Map<String, Object>) b).get(field) : null;
            int cmp = compareValues(va, vb);
            return dir == SortDirection.DESC ? -cmp : cmp;
        });
        return list;
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a instanceof Comparable && b instanceof Comparable && a.getClass().equals(b.getClass())) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private Map<String, Object> errorEntry(String ruleName, String reason) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("_ruleName", ruleName);
        error.put("_error", reason);
        return error;
    }
}

