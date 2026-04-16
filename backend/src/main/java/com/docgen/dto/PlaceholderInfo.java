package com.docgen.dto;

import java.util.List;

/**
 * Represents a parsed placeholder from a template .docx file.
 */
public record PlaceholderInfo(
        String name,
        String fullPath,
        String type,
        List<String> segments,
        List<PlaceholderInfo> children
) {}
