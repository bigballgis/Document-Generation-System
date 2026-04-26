package com.docgen.dto;

import com.docgen.entity.ComparisonType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Accepts canonical enum names plus short labels used by older API clients.
 */
public class ComparisonTypeDeserializer extends JsonDeserializer<ComparisonType> {

    @Override
    public ComparisonType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "VARIABLE", "VARIABLE_VALUE" -> ComparisonType.VARIABLE_VALUE;
            case "TEXT", "TEXT_CONTENT" -> ComparisonType.TEXT_CONTENT;
            case "SNAPSHOT", "FILE_SNAPSHOT" -> ComparisonType.FILE_SNAPSHOT;
            default -> ComparisonType.valueOf(v);
        };
    }
}
