package com.docgen.dto;

import java.util.List;

/**
 * Result of a content diff computation between two .docx files.
 *
 * @param lines          the list of diff lines
 * @param contentChanged whether the content has changed
 * @param truncated      whether the diff result was truncated due to size limits
 */
public record ContentDiffResult(
    List<ContentDiffLine> lines,
    boolean contentChanged,
    boolean truncated
) {}
