package com.docgen.service;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.dto.TemplateTestRenderOutcome;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Core service that orchestrates the complete document generation flow:
 * receive request → validate parameters → evaluate DERIVED parameters → render via Docxtemplater → optional PDF conversion → store.
 * <p>
 * Three-step pipeline: validate parameters → evaluate DERIVED parameters → render template.
 * Circuit breakers protect calls to the Docxtemplater service.
 */
@Service
public class DocumentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DocumentGeneratorService.class);

    private final TemplateRepository templateRepository;
    private final TemplateGenerationEligibilityService templateGenerationEligibilityService;
    private final ParameterValidationService parameterValidationService;
    private final RestTemplate restTemplate;
    private final DocumentStorageService documentStorageService;
    private final CircuitBreaker docxtemplaterCb;
    private final CompositeGeneratorService compositeGeneratorService;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    public DocumentGeneratorService(
            TemplateRepository templateRepository,
            TemplateGenerationEligibilityService templateGenerationEligibilityService,
            ParameterValidationService parameterValidationService,
            RestTemplate restTemplate,
            DocumentStorageService documentStorageService,
            CircuitBreaker docxtemplaterCircuitBreaker,
            CompositeGeneratorService compositeGeneratorService) {
        this.templateRepository = templateRepository;
        this.templateGenerationEligibilityService = templateGenerationEligibilityService;
        this.parameterValidationService = parameterValidationService;
        this.restTemplate = restTemplate;
        this.documentStorageService = documentStorageService;
        this.docxtemplaterCb = docxtemplaterCircuitBreaker;
        this.compositeGeneratorService = compositeGeneratorService;
    }

    /**
     * Synchronously generate a document for the given template.
     * Routes to CompositeGeneratorService for COMPOSITE templates,
     * or follows the existing single-file flow for SINGLE templates.
     * <p>
     * Async and batch callers use this overload; no sync API template version is passed.
     */
    public GenerateDocumentResponse generateDocument(Long templateId, GenerateDocumentRequest request) {
        return generateDocument(templateId, request, null);
    }

    /**
     * Same as {@link #generateDocument(Long, GenerateDocumentRequest)} with optional context from the
     * synchronous generate API.
     *
     * @param syncValidatedTemplateVersion when non-null, {@link DynamicApiService} has validated this value against
     *                                       {@code template_versions} and policy. Per {@code docs/versioned-template-generation-contract.md}
     *                                       (WS-05-T04), the render source remains the current {@link Template} row
     *                                       ({@code templateFilePath} / composite assembly), not the snapshot path
     *                                       stored on the version row.
     */
    public GenerateDocumentResponse generateDocument(Long templateId, GenerateDocumentRequest request,
                                                     Integer syncValidatedTemplateVersion) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        templateGenerationEligibilityService.requireActiveForDocumentGeneration(template, templateId);

        if (syncValidatedTemplateVersion != null) {
            log.info("Document generation for templateId={} with sync-validated template version {} "
                            + "(render source remains current template row per versioned-generation contract)",
                    templateId, syncValidatedTemplateVersion);
        }

        // Route based on template_type
        if ("COMPOSITE".equals(template.getTemplateType())) {
            log.info("Routing to composite generation for template {} (type=COMPOSITE)", templateId);
            return compositeGeneratorService.generateCompositeDocument(templateId, request);
        }

        // Existing SINGLE template flow
        Map<String, Object> params = request.getParameters() != null
                ? request.getParameters() : Collections.emptyMap();

        String outputFormat = resolveOutputFormat(request.getOutputFormat(), template.getOutputFormat());
        String storageStrategy = request.getStorageStrategy() != null
                ? request.getStorageStrategy() : template.getStorageStrategy();

        // Step 1: Execute data pipeline (fetch data + expressions)
        Map<String, Object> data = executePipeline(template, params);

        // Step 2: Render DOCX via Docxtemplater service
        byte[] docxBytes = renderDocument(template.getTemplateFilePath(), data);

        // Step 3: Single format only (WORD or PDF). BOTH is rejected — external clients must call /word and /pdf separately.
        if ("PDF".equalsIgnoreCase(outputFormat)) {
            byte[] pdfBytes = convertToPdf(docxBytes);
            return documentStorageService.store(template, pdfBytes, "PDF", storageStrategy);
        }
        return documentStorageService.store(template, docxBytes, "WORD", storageStrategy);
    }

    /**
     * Validates parameters, runs the pipeline, and renders DOCX in memory for template test execution.
     * {@link TemplateTestService} may persist a TEMP sample via {@link DocumentStorageService} after a successful render.
     */
    public TemplateTestRenderOutcome renderForTemplateTest(long templateId, Map<String, Object> parameters) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));
        // Intentionally no ACTIVE check: template tests must run against DRAFT / non-ACTIVE templates.
        Map<String, Object> params = parameters != null ? parameters : Collections.emptyMap();
        if ("COMPOSITE".equals(template.getTemplateType())) {
            return compositeGeneratorService.renderCompositeDocxInMemory(templateId, params);
        }
        Map<String, Object> data = parameterValidationService.validateAndBuildContext(templateId, params);
        byte[] docxBytes = renderDocument(template.getTemplateFilePath(), data);
        return new TemplateTestRenderOutcome(data, docxBytes);
    }

    /**
     * Three-step pipeline: validate parameters → evaluate DERIVED parameters → return data context.
     * Uses ParameterValidationService to handle validation, defaults, and DERIVED evaluation.
     */
    private Map<String, Object> executePipeline(Template template, Map<String, Object> params) {
        try {
            return parameterValidationService.validateAndBuildContext(template.getId(), params);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Parameter validation pipeline failed for template {}: {}", template.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_FAILED,
                    "参数验证管道执行失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private byte[] renderDocument(String templateFilePath, Map<String, Object> data) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("templatePath", templateFilePath);
        requestBody.put("data", data);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            return docxtemplaterCb.executeSupplier(() -> {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        docxtemplaterServiceUrl + "/render",
                        HttpMethod.POST, entity, byte[].class);

                if (response.getBody() == null || response.getBody().length == 0) {
                    throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                            "文档渲染返回空结果", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return response.getBody();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Docxtemplater render call failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                    "文档渲染服务调用失败: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("Document rendering failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                    "文档渲染失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public byte[] convertToPdf(byte[] docxBytes) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputBuffer", Base64.getEncoder().encodeToString(docxBytes));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            return docxtemplaterCb.executeSupplier(() -> {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        docxtemplaterServiceUrl + "/convert-pdf",
                        HttpMethod.POST, entity, byte[].class);

                if (response.getBody() == null || response.getBody().length == 0) {
                    throw new BusinessException(ErrorCode.GENERATE_PDF_CONVERSION_FAILED,
                            "PDF 转换返回空结果", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return response.getBody();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF conversion failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_PDF_CONVERSION_FAILED,
                    "PDF 转换失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String resolveOutputFormat(String requestFormat, String templateFormat) {
        String resolved;
        if (requestFormat != null && !requestFormat.isBlank()) {
            resolved = requestFormat.toUpperCase();
        } else {
            resolved = templateFormat != null ? templateFormat.toUpperCase() : "WORD";
        }
        rejectBothOutputFormat(resolved);
        return resolved;
    }

    /**
     * Single-request BOTH output is not supported; clients must call {@code /api/generate/{id}/word}
     * and {@code /pdf} (or async variants) separately.
     */
    public static void rejectBothOutputFormat(String outputFormat) {
        if (outputFormat != null && "BOTH".equalsIgnoreCase(outputFormat.trim())) {
            throw new BusinessException(ErrorCode.GENERATE_BOTH_NOT_SUPPORTED,
                    "Output format BOTH is not supported. Call POST /api/generate/{templateId}/word and "
                            + "/api/generate/{templateId}/pdf (or /async/word and /async/pdf) separately for each format.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}

