package com.docgen.dto;

import java.time.Instant;
import java.util.List;

public class GenerateDocumentResponse {

    private Long documentId;
    private Long templateId;
    private String format;
    private String storageStrategy;
    private String downloadUrl;
    private Long fileSize;
    private Instant generatedAt;

    /** Base64-encoded content for TEMP storage mode. */
    private String content;

    /** If BOTH format, the secondary document info. */
    private GenerateDocumentResponse secondaryDocument;

    public GenerateDocumentResponse() {}

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getStorageStrategy() { return storageStrategy; }
    public void setStorageStrategy(String storageStrategy) { this.storageStrategy = storageStrategy; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public GenerateDocumentResponse getSecondaryDocument() { return secondaryDocument; }
    public void setSecondaryDocument(GenerateDocumentResponse secondaryDocument) { this.secondaryDocument = secondaryDocument; }


    /** Per-segment render time statistics (only populated for COMPOSITE templates). */
    private List<SegmentRenderStat> segmentRenderStats;

    /** Total render time in milliseconds for all segments (only populated for COMPOSITE templates). */
    private Long totalRenderTimeMs;

    public List<SegmentRenderStat> getSegmentRenderStats() { return segmentRenderStats; }
    public void setSegmentRenderStats(List<SegmentRenderStat> segmentRenderStats) { this.segmentRenderStats = segmentRenderStats; }

    public Long getTotalRenderTimeMs() { return totalRenderTimeMs; }
    public void setTotalRenderTimeMs(Long totalRenderTimeMs) { this.totalRenderTimeMs = totalRenderTimeMs; }
}

