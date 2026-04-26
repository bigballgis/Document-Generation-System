package com.docgen.service;

import com.docgen.dto.ContentDiffLine;
import com.docgen.dto.ContentDiffLine.DiffType;
import com.docgen.dto.ContentDiffResult;
import com.docgen.dto.ExtractedText;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Service for computing content diffs between two .docx files.
 */
@Service
public class ContentDiffService {

    private static final Logger log = LoggerFactory.getLogger(ContentDiffService.class);
    static final int MAX_TEXT_BYTES = 512_000;
    static final int MAX_DIFF_LINES = 2000;

    private final DocxTextExtractor textExtractor;

    public ContentDiffService(DocxTextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    /**
     * Compute content diff between two .docx files stored in MinIO.
     * <p>
     * <strong>Identical-text contract (WS-04-T03):</strong> When extracted plain text is equal
     * ({@link String#equals(Object)}), this method returns an empty {@code lines} list,
     * {@code contentChanged=false}, and does not run the line diff algorithm (no synthetic
     * {@code EQUAL} rows). {@code truncated} is still {@code true} if either extraction was truncated.
     * Package-private {@link #computeLineDiff(String, String)} is only used when texts differ.
     * </p>
     *
     * @param oldFilePath old version file path in MinIO
     * @param newFilePath new version file path in MinIO
     * @return content diff result
     */
    public ContentDiffResult computeContentDiff(String oldFilePath, String newFilePath) {
        ExtractedText oldExtracted = textExtractor.extractTextFromMinio(oldFilePath);
        ExtractedText newExtracted = textExtractor.extractTextFromMinio(newFilePath);

        boolean anyTruncated = oldExtracted.truncated() || newExtracted.truncated();
        boolean contentChanged = !oldExtracted.text().equals(newExtracted.text());

        // Short-circuit: if content is identical, no need to run diff algorithm
        if (!contentChanged) {
            return new ContentDiffResult(Collections.emptyList(), false, anyTruncated);
        }

        List<ContentDiffLine> lines = computeLineDiff(oldExtracted.text(), newExtracted.text());

        boolean linesTruncated = false;
        if (lines.size() > MAX_DIFF_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_DIFF_LINES));
            linesTruncated = true;
        }

        return new ContentDiffResult(lines, contentChanged, anyTruncated || linesTruncated);
    }

    /**
     * Check whether content has changed between two .docx files without computing full diff.
     * Much cheaper than computeContentDiff when only the boolean flag is needed.
     *
     * @param oldFilePath old version file path in MinIO
     * @param newFilePath new version file path in MinIO
     * @return true if content differs
     */
    public boolean hasContentChanged(String oldFilePath, String newFilePath) {
        ExtractedText oldExtracted = textExtractor.extractTextFromMinio(oldFilePath);
        ExtractedText newExtracted = textExtractor.extractTextFromMinio(newFilePath);
        return !oldExtracted.text().equals(newExtracted.text());
    }

    /**
     * Compute line-by-line diff between two text strings using java-diff-utils.
     */
    List<ContentDiffLine> computeLineDiff(String oldText, String newText) {
        if (oldText == null) oldText = "";
        if (newText == null) newText = "";

        // Handle both-empty case
        if (oldText.isEmpty() && newText.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> oldLines = splitLines(oldText);
        List<String> newLines = splitLines(newText);

        Patch<String> patch = DiffUtils.diff(oldLines, newLines);

        List<ContentDiffLine> result = new ArrayList<>();
        int oldIdx = 0;
        int newIdx = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int deltaOldStart = delta.getSource().getPosition();
            int deltaNewStart = delta.getTarget().getPosition();

            // Add EQUAL lines before this delta
            while (oldIdx < deltaOldStart) {
                result.add(new ContentDiffLine(
                        DiffType.EQUAL,
                        oldIdx + 1, newIdx + 1,
                        oldLines.get(oldIdx), newLines.get(newIdx)));
                oldIdx++;
                newIdx++;
            }

            List<String> sourceLines = delta.getSource().getLines();
            List<String> targetLines = delta.getTarget().getLines();

            switch (delta.getType()) {
                case DELETE:
                    for (String line : sourceLines) {
                        result.add(new ContentDiffLine(DiffType.REMOVED, oldIdx + 1, null, line, null));
                        oldIdx++;
                    }
                    break;
                case INSERT:
                    for (String line : targetLines) {
                        result.add(new ContentDiffLine(DiffType.ADDED, null, newIdx + 1, null, line));
                        newIdx++;
                    }
                    break;
                case CHANGE:
                    int maxLen = Math.max(sourceLines.size(), targetLines.size());
                    for (int i = 0; i < maxLen; i++) {
                        String oldLine = i < sourceLines.size() ? sourceLines.get(i) : null;
                        String newLine = i < targetLines.size() ? targetLines.get(i) : null;

                        if (oldLine != null && newLine != null) {
                            result.add(new ContentDiffLine(DiffType.MODIFIED,
                                    oldIdx + 1, newIdx + 1, oldLine, newLine));
                            oldIdx++;
                            newIdx++;
                        } else if (oldLine != null) {
                            result.add(new ContentDiffLine(DiffType.REMOVED, oldIdx + 1, null, oldLine, null));
                            oldIdx++;
                        } else {
                            result.add(new ContentDiffLine(DiffType.ADDED, null, newIdx + 1, null, newLine));
                            newIdx++;
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        // Add remaining EQUAL lines after last delta
        while (oldIdx < oldLines.size()) {
            result.add(new ContentDiffLine(
                    DiffType.EQUAL,
                    oldIdx + 1, newIdx + 1,
                    oldLines.get(oldIdx), newLines.get(newIdx)));
            oldIdx++;
            newIdx++;
        }

        return result;
    }

    private List<String> splitLines(String text) {
        if (text.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(text.split("\n", -1));
    }
}
