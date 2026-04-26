package com.docgen.dto;

/**
 * Result of text extraction from a .docx file.
 *
 * @param text      the extracted plain text content
 * @param truncated whether the text was truncated due to size limits
 */
public record ExtractedText(String text, boolean truncated) {}
