package com.docgen.service;

import com.docgen.dto.PreviewRequest;
import com.docgen.dto.PreviewResult;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Service for generating template previews.
 * Supports user-provided test data or real data source preview.
 * Supports OnlyOffice in-browser preview and direct download.
 */
@Service
public class TemplatePreviewService {

    private static final Logger log = LoggerFactory.getLogger(TemplatePreviewService.class);

    private final TemplateRepository templateRepository;
    private final DataAggregationService dataAggregationService;
    private final DocumentGeneratorService documentGeneratorService;
    private final RestTemplate restTemplate;
    private final MinioClient minioClient;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${onlyoffice.url:http://localhost}")
    private String onlyOfficeUrl;

    public TemplatePreviewService(TemplateRepository templateRepository,
                                   DataAggregationService dataAggregationService,
                                   DocumentGeneratorService documentGeneratorService,
                                   RestTemplate restTemplate,
                                   MinioClient minioClient) {
        this.templateRepository = templateRepository;
        this.dataAggregationService = dataAggregationService;
        this.documentGeneratorService = documentGeneratorService;
        this.restTemplate = restTemplate;
        this.minioClient = minioClient;
    }

    /**
     * Generate a preview document for the given template.
     */
    public PreviewResult preview(Long templateId, PreviewRequest request) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        // Resolve data: use test data or real data sources
        Map<String, Object> data;
        if (request.isUseRealDataSources()) {
            Map<String, Object> params = request.getParameters() != null
                    ? request.getParameters() : Collections.emptyMap();
            data = dataAggregationService.aggregateData(templateId, params);
        } else {
            data = request.getTestData() != null ? request.getTestData() : Collections.emptyMap();
        }

        // Render the document via Docxtemplater service
        byte[] docxBytes = renderPreview(template.getTemplateFilePath(), data);

        // Convert to PDF if requested
        String format = request.getOutputFormat() != null ? request.getOutputFormat().toUpperCase() : "WORD";
        byte[] outputBytes = docxBytes;
        if ("PDF".equals(format)) {
            outputBytes = documentGeneratorService.convertToPdf(docxBytes);
        }

        PreviewResult result = new PreviewResult();
        result.setFormat(format);
        result.setFileSize((long) outputBytes.length);
        result.setContent(Base64.getEncoder().encodeToString(outputBytes));

        // If OnlyOffice preview requested, upload to MinIO and generate editor URL
        if (request.isOnlyOfficePreview() && "WORD".equals(format)) {
            String previewUrl = uploadPreviewForOnlyOffice(template, docxBytes);
            result.setOnlyOfficeUrl(previewUrl);
        }

        return result;
    }

    private byte[] renderPreview(String templateFilePath, Map<String, Object> data) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("templatePath", templateFilePath);
        requestBody.put("data", data);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    docxtemplaterServiceUrl + "/render",
                    HttpMethod.POST, entity, byte[].class);

            if (response.getBody() == null || response.getBody().length == 0) {
                throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                        "预览渲染返回空结果", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return response.getBody();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Preview rendering failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                    "预览渲染失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Upload preview document to MinIO and return a URL suitable for OnlyOffice editor.
     */
    private String uploadPreviewForOnlyOffice(Template template, byte[] docxBytes) {
        String objectPath = String.format("preview/%d/%s.docx", template.getId(), UUID.randomUUID());

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(docxBytes), docxBytes.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());

            // Return the MinIO URL that OnlyOffice can access
            return String.format("%s/%s/%s", minioEndpoint, bucketName, objectPath);
        } catch (Exception e) {
            log.warn("Failed to upload preview for OnlyOffice: {}", e.getMessage());
            return null;
        }
    }
}
