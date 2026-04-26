package com.docgen.service;

import com.docgen.dto.MergeDocumentsRequest;
import com.docgen.entity.GeneratedDocument;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.GeneratedDocumentRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for merging multiple generated Word documents into a single document.
 * <p>
 * The actual document merging is delegated to the Docxtemplater Node.js service
 * via its {@code POST /merge-segments} endpoint (base64 segment buffers).
 * This service handles validation, document retrieval from MinIO, and storage of the merged result.
 */
@Service
public class DocumentMergeService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMergeService.class);

    private final GeneratedDocumentRepository documentRepository;
    private final RestTemplate restTemplate;
    private final MinioClient minioClient;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public DocumentMergeService(GeneratedDocumentRepository documentRepository,
                                RestTemplate restTemplate,
                                MinioClient minioClient) {
        this.documentRepository = documentRepository;
        this.restTemplate = restTemplate;
        this.minioClient = minioClient;
    }

    /**
     * Merge multiple generated documents into one.
     *
     * @param request merge request containing document IDs and options
     * @return the merged {@link GeneratedDocument} entity (persisted)
     */
    public GeneratedDocument mergeDocuments(MergeDocumentsRequest request) {
        validateRequest(request);

        List<Long> documentIds = request.getDocumentIds();

        // Validate all document IDs exist and collect entities in order
        List<GeneratedDocument> documents = new ArrayList<>();
        List<Long> invalidIds = new ArrayList<>();

        for (Long id : documentIds) {
            Optional<GeneratedDocument> opt = documentRepository.findById(id);
            if (opt.isPresent()) {
                documents.add(opt.get());
            } else {
                invalidIds.add(id);
            }
        }

        if (!invalidIds.isEmpty()) {
            throw new BusinessException(ErrorCode.MERGE_INVALID_DOCUMENT_IDS,
                    "无效的文档 ID: " + invalidIds, HttpStatus.BAD_REQUEST);
        }

        // Fetch document contents from MinIO in the specified order
        List<String> base64Documents = new ArrayList<>();
        for (GeneratedDocument doc : documents) {
            byte[] content = fetchDocumentContent(doc);
            base64Documents.add(Base64.getEncoder().encodeToString(content));
        }

        // Delegate merging to Node.js service
        byte[] mergedContent = callMergeEndpoint(base64Documents,
                request.isInsertPageBreaks(), request.isGenerateToc(),
                request.getOutputFormat());

        // Store merged document
        String format = resolveFormat(request.getOutputFormat());
        String objectPath = "merged/" + UUID.randomUUID() + "." + format.toLowerCase();

        uploadToMinio(objectPath, mergedContent, format);

        GeneratedDocument merged = new GeneratedDocument();
        merged.setTemplateId(documents.get(0).getTemplateId());
        merged.setTenantId(documents.get(0).getTenantId());
        merged.setFilePath(objectPath);
        merged.setFormat(format);
        merged.setStatus("COMPLETED");
        merged.setStorageStrategy("PERSISTENT");
        merged.setFileSize((long) mergedContent.length);
        merged.setMetadata("{\"mergedFrom\":" + documentIds + "}");
        merged.setGeneratedAt(Instant.now());

        return documentRepository.save(merged);
    }

    // ── Validation ──

    void validateRequest(MergeDocumentsRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.MERGE_INVALID_REQUEST,
                    "合并请求不能为空", HttpStatus.BAD_REQUEST);
        }
        if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            throw new BusinessException(ErrorCode.MERGE_INVALID_REQUEST,
                    "文档 ID 列表不能为空", HttpStatus.BAD_REQUEST);
        }
        if (request.getDocumentIds().size() < 2) {
            throw new BusinessException(ErrorCode.MERGE_INVALID_REQUEST,
                    "至少需要 2 个文档才能合并", HttpStatus.BAD_REQUEST);
        }
        String format = request.getOutputFormat();
        if (format != null && !format.isBlank()
                && !"DOCX".equalsIgnoreCase(format) && !"PDF".equalsIgnoreCase(format)) {
            throw new BusinessException(ErrorCode.MERGE_INVALID_REQUEST,
                    "不支持的输出格式: " + format + "，仅支持 DOCX 或 PDF",
                    HttpStatus.BAD_REQUEST);
        }
    }

    // ── MinIO operations ──

    byte[] fetchDocumentContent(GeneratedDocument doc) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(doc.getFilePath())
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to fetch document {} from MinIO: {}", doc.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.DOCUMENT_DOWNLOAD_FAILED,
                    "获取文档内容失败: " + doc.getId(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private void uploadToMinio(String objectPath, byte[] content, String format) {
        String contentType = "PDF".equalsIgnoreCase(format)
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload merged document to MinIO: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_STORAGE_FAILED,
                    "合并文档存储失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    // ── Node.js service call ──

    /**
     * Builds the {@code segments} array expected by {@code POST /merge-segments}.
     */
    static List<Map<String, Object>> buildMergeSegmentsPayload(List<String> base64Documents,
                                                               boolean insertPageBreaks) {
        List<Map<String, Object>> segments = new ArrayList<>();
        for (int i = 0; i < base64Documents.size(); i++) {
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("buffer", base64Documents.get(i));
            if (i > 0) {
                seg.put("pageBreakBefore", insertPageBreaks);
            }
            segments.add(seg);
        }
        return segments;
    }

    /**
     * @param generateToc reserved for product metadata; not sent to merge-segments
     * @param outputFormat  reserved for persisted format; merge-segments always returns DOCX bytes
     */
    @SuppressWarnings("unused")
    byte[] callMergeEndpoint(List<String> base64Documents, boolean insertPageBreaks,
                             boolean generateToc, String outputFormat) {
        Map<String, Object> body = new HashMap<>();
        body.put("segments", buildMergeSegmentsPayload(base64Documents, insertPageBreaks));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    docxtemplaterServiceUrl + "/merge-segments",
                    HttpMethod.POST, entity, byte[].class);

            if (response.getBody() == null || response.getBody().length == 0) {
                throw new BusinessException(ErrorCode.MERGE_FAILED,
                        "合并服务返回空结果", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return response.getBody();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Merge service call failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.MERGE_FAILED,
                    "文档合并服务调用失败: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("Document merge failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.MERGE_FAILED,
                    "文档合并失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String resolveFormat(String outputFormat) {
        if ("PDF".equalsIgnoreCase(outputFormat)) {
            return "PDF";
        }
        return "DOCX";
    }
}
