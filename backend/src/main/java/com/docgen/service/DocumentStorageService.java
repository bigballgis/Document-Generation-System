package com.docgen.service;

import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.dto.GeneratedDocumentDTO;
import com.docgen.entity.GeneratedDocument;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.GeneratedDocumentRepository;
import com.docgen.util.TenantContext;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for storing generated documents.
 * Supports TEMP (return content directly with auto-cleanup) and PERSISTENT (upload to MinIO) modes.
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private final MinioClient minioClient;
    private final GeneratedDocumentRepository documentRepository;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${document.temp-cleanup-hours:24}")
    private int tempCleanupHours;

    public DocumentStorageService(MinioClient minioClient,
                                  GeneratedDocumentRepository documentRepository) {
        this.minioClient = minioClient;
        this.documentRepository = documentRepository;
    }

    /**
     * Store a generated document according to the storage strategy.
     */
    public GenerateDocumentResponse store(Template template, byte[] content, String format, String storageStrategy) {
        GenerateDocumentResponse response = new GenerateDocumentResponse();
        response.setTemplateId(template.getId());
        response.setFormat(format);
        response.setStorageStrategy(storageStrategy);
        response.setFileSize((long) content.length);
        response.setGeneratedAt(Instant.now());

        if ("PERSISTENT".equalsIgnoreCase(storageStrategy)) {
            return storePersistent(template, content, format, response);
        } else {
            return storeTemp(template, content, format, response);
        }
    }

    private GenerateDocumentResponse storeTemp(Template template, byte[] content,
                                                String format, GenerateDocumentResponse response) {
        // Upload to MinIO for temp download link
        String extension = "PDF".equalsIgnoreCase(format) ? ".pdf" : ".docx";
        String contentType = resolveContentType(format);
        String objectPath = String.format("temp/%d/%s%s", template.getId(), UUID.randomUUID(), extension);

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to store temp document to MinIO, returning inline content: {}", e.getMessage());
            response.setContent(Base64.getEncoder().encodeToString(content));
            return response;
        }

        // Save metadata to DB with expiration
        Instant expiresAt = Instant.now().plus(tempCleanupHours, ChronoUnit.HOURS);
        GeneratedDocument doc = new GeneratedDocument();
        doc.setTemplateId(template.getId());
        doc.setTenantId(template.getTenantId());
        doc.setFilePath(objectPath);
        doc.setFormat(format);
        doc.setStatus("COMPLETED");
        doc.setStorageStrategy("TEMP");
        doc.setFileSize((long) content.length);
        doc.setGeneratedAt(Instant.now());
        doc.setExpiresAt(expiresAt);

        String downloadUrl = String.format("/api/documents/%s/download", "pending");
        doc.setDownloadUrl(downloadUrl);
        doc = documentRepository.save(doc);

        // Update download URL with actual ID
        doc.setDownloadUrl(String.format("/api/documents/%d/download", doc.getId()));
        documentRepository.save(doc);

        response.setDocumentId(doc.getId());
        response.setDownloadUrl(doc.getDownloadUrl());
        response.setContent(Base64.getEncoder().encodeToString(content));
        return response;
    }

    private GenerateDocumentResponse storePersistent(Template template, byte[] content,
                                                      String format, GenerateDocumentResponse response) {
        String extension = "PDF".equalsIgnoreCase(format) ? ".pdf" : ".docx";
        String contentType = resolveContentType(format);
        String objectPath = String.format("generated/%d/%s%s", template.getId(), UUID.randomUUID(), extension);

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to store document to MinIO: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_STORAGE_FAILED,
                    "Document storage failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        // Save metadata to DB
        GeneratedDocument doc = new GeneratedDocument();
        doc.setTemplateId(template.getId());
        doc.setTenantId(template.getTenantId());
        doc.setFilePath(objectPath);
        doc.setFormat(format);
        doc.setStatus("COMPLETED");
        doc.setStorageStrategy("PERSISTENT");
        doc.setFileSize((long) content.length);
        doc.setGeneratedAt(Instant.now());

        String downloadUrl = String.format("/api/documents/%s/download", "pending");
        doc.setDownloadUrl(downloadUrl);
        doc = documentRepository.save(doc);

        doc.setDownloadUrl(String.format("/api/documents/%d/download", doc.getId()));
        documentRepository.save(doc);

        response.setDocumentId(doc.getId());
        response.setDownloadUrl(doc.getDownloadUrl());
        log.info("Document stored persistently to MinIO: {}", objectPath);
        return response;
    }

    /**
     * Download document content by ID.
     */
    public byte[] downloadDocument(Long documentId) {
        GeneratedDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND,
                        "Document not found: " + documentId, HttpStatus.NOT_FOUND));

        if (doc.getExpiresAt() != null && doc.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.DOCUMENT_EXPIRED,
                    "Document expired: " + documentId, HttpStatus.GONE);
        }

        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(doc.getFilePath())
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download document from MinIO: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DOCUMENT_DOWNLOAD_FAILED,
                    "Document download failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Get document metadata by ID.
     */
    public GeneratedDocumentDTO getDocument(Long documentId) {
        GeneratedDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND,
                        "Document not found: " + documentId, HttpStatus.NOT_FOUND));
        return toDTO(doc);
    }

    /**
     * Query historical documents with filters.
     */
    @Transactional(readOnly = true)
    public Page<GeneratedDocumentDTO> listDocuments(Long templateId, String status,
                                                     Instant startTime, Instant endTime,
                                                     Pageable pageable) {
        return documentRepository.findByFilters(templateId, status, startTime, endTime, pageable)
                .map(this::toDTO);
    }

    /**
     * Scheduled cleanup of expired temporary documents.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredTempDocuments() {
        Instant now = Instant.now();
        List<GeneratedDocument> expired = documentRepository.findExpiredTempDocuments(now);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Cleaning up {} expired temporary documents", expired.size());
        for (GeneratedDocument doc : expired) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(doc.getFilePath())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to remove expired file from MinIO: {}", e.getMessage());
            }
        }

        int deleted = documentRepository.deleteExpiredTempDocuments(now);
        log.info("Deleted {} expired temporary document records", deleted);
    }

    /**
     * Get the format's content type.
     */
    public String getContentType(Long documentId) {
        GeneratedDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND,
                        "Document not found: " + documentId, HttpStatus.NOT_FOUND));
        return resolveContentType(doc.getFormat());
    }

    private String resolveContentType(String format) {
        return "PDF".equalsIgnoreCase(format)
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
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
