package com.docgen.dto;

import java.util.List;

/**
 * Result of assembling a composite document from multiple segments.
 * Contains the merged document bytes and per-segment render results.
 */
public class AssemblyResult {

    private byte[] documentBytes;
    private List<SegmentRenderResult> segmentResults;
    private long totalRenderTimeMs;

    public AssemblyResult() {}

    public byte[] getDocumentBytes() { return documentBytes; }
    public void setDocumentBytes(byte[] documentBytes) { this.documentBytes = documentBytes; }

    public List<SegmentRenderResult> getSegmentResults() { return segmentResults; }
    public void setSegmentResults(List<SegmentRenderResult> segmentResults) { this.segmentResults = segmentResults; }

    public long getTotalRenderTimeMs() { return totalRenderTimeMs; }
    public void setTotalRenderTimeMs(long totalRenderTimeMs) { this.totalRenderTimeMs = totalRenderTimeMs; }
}
