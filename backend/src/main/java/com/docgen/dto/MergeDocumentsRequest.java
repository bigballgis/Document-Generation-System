package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class MergeDocumentsRequest {

    @NotEmpty(message = "文档 ID 列表不能为空")
    private List<Long> documentIds;

    private boolean insertPageBreaks = true;

    private boolean generateToc = false;

    /** Output format: DOCX (default) or PDF. */
    private String outputFormat = "DOCX";

    public MergeDocumentsRequest() {}

    public List<Long> getDocumentIds() { return documentIds; }
    public void setDocumentIds(List<Long> documentIds) { this.documentIds = documentIds; }

    public boolean isInsertPageBreaks() { return insertPageBreaks; }
    public void setInsertPageBreaks(boolean insertPageBreaks) { this.insertPageBreaks = insertPageBreaks; }

    public boolean isGenerateToc() { return generateToc; }
    public void setGenerateToc(boolean generateToc) { this.generateToc = generateToc; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
}
