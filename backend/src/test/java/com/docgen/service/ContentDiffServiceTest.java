package com.docgen.service;

import com.docgen.dto.ContentDiffLine;
import com.docgen.dto.ContentDiffLine.DiffType;
import com.docgen.dto.ContentDiffResult;
import com.docgen.dto.ExtractedText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContentDiffService} line diff and MinIO-backed diff entry points.
 * WS-04-T01: characterize current behavior before diff contract changes.
 */
@ExtendWith(MockitoExtension.class)
class ContentDiffServiceTest {

    private static final String OLD_PATH = "segments/1/old.docx";
    private static final String NEW_PATH = "segments/1/new.docx";

    @Mock
    private DocxTextExtractor textExtractor;

    private ContentDiffService service;

    @BeforeEach
    void setUp() {
        service = new ContentDiffService(textExtractor);
    }

    // ── computeLineDiff (package-private) ──

    @Test
    void computeLineDiff_bothEmpty_returnsEmptyList() {
        assertTrue(service.computeLineDiff("", "").isEmpty());
    }

    @Test
    void computeLineDiff_nullTreatedAsEmpty_addsLineFromOtherSide() {
        List<ContentDiffLine> lines = service.computeLineDiff(null, "only-new");
        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().anyMatch(l -> l.getType() == DiffType.ADDED));
    }

    @Test
    void computeLineDiff_identicalSingleLine_returnsOneEqualLine() {
        List<ContentDiffLine> lines = service.computeLineDiff("alpha", "alpha");
        assertEquals(1, lines.size());
        assertEquals(DiffType.EQUAL, lines.get(0).getType());
        assertEquals("alpha", lines.get(0).getOldText());
        assertEquals("alpha", lines.get(0).getNewText());
        assertEquals(1, lines.get(0).getOldLineNumber());
        assertEquals(1, lines.get(0).getNewLineNumber());
    }

    @Test
    void computeLineDiff_addedSecondLine_containsAdded() {
        List<ContentDiffLine> lines = service.computeLineDiff("first", "first\nsecond");
        assertTrue(lines.stream().anyMatch(l -> l.getType() == DiffType.EQUAL && "first".equals(l.getOldText())));
        assertTrue(lines.stream().anyMatch(l -> l.getType() == DiffType.ADDED && "second".equals(l.getNewText())));
    }

    @Test
    void computeLineDiff_removedSecondLine_containsRemoved() {
        List<ContentDiffLine> lines = service.computeLineDiff("first\nsecond", "first");
        assertTrue(lines.stream().anyMatch(l -> l.getType() == DiffType.EQUAL && "first".equals(l.getOldText())));
        assertTrue(lines.stream().anyMatch(l -> l.getType() == DiffType.REMOVED && "second".equals(l.getOldText())));
    }

    @Test
    void computeLineDiff_replacedLine_containsModified() {
        List<ContentDiffLine> lines = service.computeLineDiff("header\nold-body", "header\nnew-body");
        ContentDiffLine mod = lines.stream()
                .filter(l -> l.getType() == DiffType.MODIFIED)
                .findFirst()
                .orElseThrow();
        assertEquals("old-body", mod.getOldText());
        assertEquals("new-body", mod.getNewText());
    }

    // ── computeContentDiff / hasContentChanged ──

    @Test
    void computeContentDiff_identicalTexts_returnsEmptyLinesAndNoContentChange() {
        when(textExtractor.extractTextFromMinio(eq(OLD_PATH))).thenReturn(new ExtractedText("same\nbody", false));
        when(textExtractor.extractTextFromMinio(eq(NEW_PATH))).thenReturn(new ExtractedText("same\nbody", false));

        ContentDiffResult result = service.computeContentDiff(OLD_PATH, NEW_PATH);

        assertNotNull(result);
        assertTrue(result.lines().isEmpty());
        assertFalse(result.contentChanged());
        assertFalse(result.truncated());
        verify(textExtractor).extractTextFromMinio(OLD_PATH);
        verify(textExtractor).extractTextFromMinio(NEW_PATH);
    }

    @Test
    void computeContentDiff_identicalTexts_butExtractTruncated_stillNoDiffLines_truncatedFlagSet() {
        when(textExtractor.extractTextFromMinio(eq(OLD_PATH))).thenReturn(new ExtractedText("x", true));
        when(textExtractor.extractTextFromMinio(eq(NEW_PATH))).thenReturn(new ExtractedText("x", false));

        ContentDiffResult result = service.computeContentDiff(OLD_PATH, NEW_PATH);

        assertTrue(result.lines().isEmpty());
        assertFalse(result.contentChanged());
        assertTrue(result.truncated());
    }

    @Test
    void computeContentDiff_differentTexts_returnsNonEmptyDiff() {
        when(textExtractor.extractTextFromMinio(eq(OLD_PATH))).thenReturn(new ExtractedText("a", false));
        when(textExtractor.extractTextFromMinio(eq(NEW_PATH))).thenReturn(new ExtractedText("b", false));

        ContentDiffResult result = service.computeContentDiff(OLD_PATH, NEW_PATH);

        assertTrue(result.contentChanged());
        assertFalse(result.lines().isEmpty());
    }

    @Test
    void hasContentChanged_delegatesToExtractor() {
        when(textExtractor.extractTextFromMinio(eq(OLD_PATH))).thenReturn(new ExtractedText("1", false));
        when(textExtractor.extractTextFromMinio(eq(NEW_PATH))).thenReturn(new ExtractedText("2", false));

        assertTrue(service.hasContentChanged(OLD_PATH, NEW_PATH));
    }

    @Test
    void hasContentChanged_falseWhenEqual() {
        when(textExtractor.extractTextFromMinio(eq(OLD_PATH))).thenReturn(new ExtractedText("z", false));
        when(textExtractor.extractTextFromMinio(eq(NEW_PATH))).thenReturn(new ExtractedText("z", false));

        assertFalse(service.hasContentChanged(OLD_PATH, NEW_PATH));
    }
}
