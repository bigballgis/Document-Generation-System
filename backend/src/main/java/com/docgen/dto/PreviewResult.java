package com.docgen.dto;

/**
 * Result DTO for template preview.
 */
public class PreviewResult {

    /** Base64-encoded document content for download. */
    private String content;

    /** Document format (WORD or PDF). */
    private String format;

    /** File size in bytes. */
    private Long fileSize;

    /** Download URL for the preview document. */
    private String downloadUrl;

    /** OnlyOffice editor URL for in-browser preview (if requested). */
    private String onlyOfficeUrl;

    public PreviewResult() {}

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getOnlyOfficeUrl() { return onlyOfficeUrl; }
    public void setOnlyOfficeUrl(String onlyOfficeUrl) { this.onlyOfficeUrl = onlyOfficeUrl; }
}
