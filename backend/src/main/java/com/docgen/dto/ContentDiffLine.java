package com.docgen.dto;

/**
 * Represents a single line in a content diff result.
 */
public class ContentDiffLine {

    public enum DiffType { EQUAL, ADDED, REMOVED, MODIFIED }

    private DiffType type;
    private Integer oldLineNumber;  // null for ADDED
    private Integer newLineNumber;  // null for REMOVED
    private String oldText;         // null for ADDED
    private String newText;         // null for REMOVED

    public ContentDiffLine() {}

    public ContentDiffLine(DiffType type, Integer oldLineNumber, Integer newLineNumber,
                           String oldText, String newText) {
        this.type = type;
        this.oldLineNumber = oldLineNumber;
        this.newLineNumber = newLineNumber;
        this.oldText = oldText;
        this.newText = newText;
    }

    public DiffType getType() { return type; }
    public void setType(DiffType type) { this.type = type; }

    public Integer getOldLineNumber() { return oldLineNumber; }
    public void setOldLineNumber(Integer oldLineNumber) { this.oldLineNumber = oldLineNumber; }

    public Integer getNewLineNumber() { return newLineNumber; }
    public void setNewLineNumber(Integer newLineNumber) { this.newLineNumber = newLineNumber; }

    public String getOldText() { return oldText; }
    public void setOldText(String oldText) { this.oldText = oldText; }

    public String getNewText() { return newText; }
    public void setNewText(String newText) { this.newText = newText; }
}
