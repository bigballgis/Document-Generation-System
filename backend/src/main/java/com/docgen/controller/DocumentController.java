package com.docgen.controller;

import com.docgen.dto.GeneratedDocumentDTO;
import com.docgen.dto.MergeDocumentsRequest;
import com.docgen.entity.GeneratedDocument;
import com.docgen.service.DocumentMergeService;
import com.docgen.service.DocumentStorageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for querying and downloading generated documents.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentStorageService documentStorageService;
    private final DocumentMergeService documentMergeService;

    public DocumentController(DocumentStorageService documentStorageService,
                              DocumentMergeService documentMergeService) {
        this.documentStorageService = documentStorageService;
        this.documentMergeService = documentMergeService;
    }

    /**
     * Query historical generated documents with optional filters.
     */
    @GetMapping
    public ResponseEntity<Page<GeneratedDocumentDTO>> listDocuments(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            Pageable pageable) {
        return ResponseEntity.ok(
                documentStorageService.listDocuments(templateId, status, startTime, endTime, pageable));
    }

    /**
     * Get document metadata by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<GeneratedDocumentDTO> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentStorageService.getDocument(id));
    }

    /**
     * Download a generated document by ID.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id) {
        byte[] content = documentStorageService.downloadDocument(id);
        String contentType = documentStorageService.getContentType(id);

        String extension = contentType.contains("pdf") ? ".pdf" : ".docx";
        String filename = "document-" + id + extension;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

    /**
     * Merge multiple generated documents into one.
     */
    @PostMapping("/merge")
    public ResponseEntity<GeneratedDocumentDTO> mergeDocuments(
            @Valid @RequestBody MergeDocumentsRequest request) {
        GeneratedDocument merged = documentMergeService.mergeDocuments(request);
        return ResponseEntity.ok(toDTO(merged));
    }

    private GeneratedDocumentDTO toDTO(GeneratedDocument doc) {
        GeneratedDocumentDTO dto = new GeneratedDocumentDTO();
        dto.setId(doc.getId());
        dto.setTemplateId(doc.getTemplateId());
        dto.setFilePath(doc.getFilePath());
        dto.setFormat(doc.getFormat());
        dto.setStatus(doc.getStatus());
        dto.setStorageStrategy(doc.getStorageStrategy());
        dto.setFileSize(doc.getFileSize());
        dto.setPageCount(doc.getPageCount());
        dto.setDownloadUrl(doc.getDownloadUrl());
        dto.setGeneratedAt(doc.getGeneratedAt());
        dto.setExpiresAt(doc.getExpiresAt());
        return dto;
    }
}
