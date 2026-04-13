package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for OnlyOffice Document Server integration.
 *
 * <p>Generates presigned MinIO URLs so that OnlyOffice can download template files
 * directly from MinIO without going through the authenticated backend API.
 *
 * <p>Handles OnlyOffice save callbacks by downloading the edited document from
 * the URL provided by OnlyOffice and updating the template file in MinIO.
 */
@Service
public class OnlyOfficeService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeService.class);

    /** Presigned URL validity duration in minutes. */
    private static final int PRESIGNED_URL_EXPIRY_MINUTES = 60;

    private final TemplateRepository templateRepository;
    private final MinioClient minioClient;
    private final RestTemplate restTemplate;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    @Value("${minio.external-endpoint:${minio.endpoint:http://localhost:9000}}")
    private String minioExternalEndpoint;

    public OnlyOfficeService(TemplateRepository templateRepository,
                             MinioClient minioClient,
                             RestTemplate restTemplate) {
        this.templateRepository = templateRepository;
        this.minioClient = minioClient;
        this.restTemplate = restTemplate;
    }

    /**
     * Generate a presigned GET URL for the template file in MinIO.
     * OnlyOffice Document Server will use this URL to download the document.
     */
    public String generatePresignedUrl(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(template.getTemplateFilePath())
                            .expiry(PRESIGNED_URL_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for template {}: {}",
                    templateId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ONLYOFFICE_URL_FAILED,
                    "生成文档访问链接失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Handle OnlyOffice Document Server callback.
     *
     * <p>Status codes from OnlyOffice:
     * <ul>
     *   <li>1 — document is being edited</li>
     *   <li>2 — document is ready for saving (all editors closed)</li>
     *   <li>4 — document closed with no changes</li>
     *   <li>6 — document is being edited, but the current state is saved (forcesave)</li>
     *   <li>7 — error has occurred while force saving the document</li>
     * </ul>
     */
    public void handleCallback(Long templateId, Map<String, Object> body) {
        int status = body.get("status") instanceof Number
                ? ((Number) body.get("status")).intValue() : 0;

        // Status 2 (ready for saving) or 6 (forcesave) — download and update
        if (status == 2 || status == 6) {
            String downloadUrl = (String) body.get("url");
            if (downloadUrl == null || downloadUrl.isBlank()) {
                log.warn("OnlyOffice callback for template {} has no download URL", templateId);
                return;
            }
            saveEditedDocument(templateId, downloadUrl);
        }
    }

    private void saveEditedDocument(Long templateId, String downloadUrl) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        try {
            // Download the edited document from OnlyOffice
            byte[] editedContent = restTemplate.getForObject(downloadUrl, byte[].class);
            if (editedContent == null || editedContent.length == 0) {
                log.warn("Downloaded empty content from OnlyOffice for template {}", templateId);
                return;
            }

            // Overwrite the template file in MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(template.getTemplateFilePath())
                    .stream(new ByteArrayInputStream(editedContent), editedContent.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());

            log.info("Saved edited document from OnlyOffice for template {}, size={} bytes",
                    templateId, editedContent.length);
        } catch (Exception e) {
            log.error("Failed to save OnlyOffice edited document for template {}: {}",
                    templateId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                    "保存编辑文档失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
