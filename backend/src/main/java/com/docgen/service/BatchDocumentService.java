package com.docgen.service;

import com.docgen.dto.AsyncTaskDTO;
import com.docgen.dto.BatchGenerateRequest;
import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.entity.AsyncTask;
import com.docgen.entity.GeneratedDocument;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.AsyncTaskRepository;
import com.docgen.repository.GeneratedDocumentRepository;
import com.docgen.repository.TemplateRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for batch document generation.
 * Uses Spring's @Async for background processing.
 * Generates individual documents for each data set and packages them into a ZIP.
 */
@Service
public class BatchDocumentService {

    private static final Logger log = LoggerFactory.getLogger(BatchDocumentService.class);
    private static final int MAX_BATCH_SIZE = 1000;

    private final AsyncTaskRepository asyncTaskRepository;
    private final TemplateRepository templateRepository;
    private final TemplateGenerationEligibilityService templateGenerationEligibilityService;
    private final GeneratedDocumentRepository documentRepository;
    private final DocumentGeneratorService documentGeneratorService;
    private final DocumentStorageService documentStorageService;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public BatchDocumentService(AsyncTaskRepository asyncTaskRepository,
                                TemplateRepository templateRepository,
                                TemplateGenerationEligibilityService templateGenerationEligibilityService,
                                GeneratedDocumentRepository documentRepository,
                                DocumentGeneratorService documentGeneratorService,
                                DocumentStorageService documentStorageService,
                                MinioClient minioClient) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.templateRepository = templateRepository;
        this.templateGenerationEligibilityService = templateGenerationEligibilityService;
        this.documentRepository = documentRepository;
        this.documentGeneratorService = documentGeneratorService;
        this.documentStorageService = documentStorageService;
        this.minioClient = minioClient;
    }

    /**
     * Submit a batch document generation task.
     */
    @Transactional
    public AsyncTaskDTO submitBatchGeneration(Long templateId, BatchGenerateRequest request) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "Template not found: " + templateId, HttpStatus.NOT_FOUND));

        templateGenerationEligibilityService.requireActiveForDocumentGeneration(template, templateId);

        if (request.getDataSets() == null || request.getDataSets().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Data sets must not be empty", HttpStatus.BAD_REQUEST);
        }

        if (request.getDataSets().size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.GENERATE_BATCH_LIMIT_EXCEEDED,
                    "Batch generation supports at most " + MAX_BATCH_SIZE + " documents",
                    HttpStatus.BAD_REQUEST);
        }

        AsyncTask task = new AsyncTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType("BATCH_GENERATE");
        task.setTemplateId(templateId);
        task.setTenantId(template.getTenantId());
        task.setStatus("PENDING");
        task.setTotalCount(request.getDataSets().size());
        task = asyncTaskRepository.save(task);

        // Trigger async batch processing
        processBatchGeneration(task.getTaskId(), templateId, request);

        return toDTO(task);
    }

    /**
     * Process batch generation in the background.
     */
    @Async
    public void processBatchGeneration(String taskId, Long templateId, BatchGenerateRequest request) {
        AsyncTask task = asyncTaskRepository.findByTaskId(taskId).orElse(null);
        if (task == null) return;

        try {
            task.setStatus("RUNNING");
            asyncTaskRepository.save(task);

            Template template = templateRepository.findById(templateId).orElse(null);
            if (template == null) {
                task.setStatus("FAILED");
                task.setErrorMessage("Template not found: " + templateId);
                task.setCompletedAt(Instant.now());
                asyncTaskRepository.save(task);
                return;
            }

            try {
                templateGenerationEligibilityService.requireActiveForDocumentGeneration(template, templateId);
            } catch (BusinessException e) {
                task.setStatus("FAILED");
                task.setErrorMessage(e.getMessage());
                task.setCompletedAt(Instant.now());
                asyncTaskRepository.save(task);
                return;
            }

            List<Map<String, Object>> dataSets = request.getDataSets();
            String failureStrategy = request.getFailureStrategy() != null
                    ? request.getFailureStrategy() : "CONTINUE";
            int failureThreshold = request.getFailureThreshold();

            List<byte[]> successDocuments = new ArrayList<>();
            List<String> documentNames = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            List<Map<String, Object>> itemResults = new ArrayList<>();

            String outputFormat = DocumentGeneratorService.resolveSingleDocumentOutputFormat(
                    request.getOutputFormat(), template.getOutputFormat(), template.getId());
            String extension = "PDF".equalsIgnoreCase(outputFormat) ? ".pdf" : ".docx";

            for (int i = 0; i < dataSets.size(); i++) {
                Map<String, Object> dataSet = dataSets.get(i);
                Map<String, Object> itemResult = new LinkedHashMap<>();
                itemResult.put("index", i);

                try {
                    GenerateDocumentRequest genRequest = new GenerateDocumentRequest();
                    genRequest.setParameters(dataSet);
                    genRequest.setOutputFormat(outputFormat);
                    genRequest.setStorageStrategy("TEMP");

                    GenerateDocumentResponse response = documentGeneratorService.generateDocument(templateId, genRequest, null);

                    // Download the generated content for ZIP packaging
                    if (response.getDocumentId() != null) {
                        byte[] content = documentStorageService.downloadDocument(response.getDocumentId());
                        successDocuments.add(content);
                        documentNames.add("document_" + (i + 1) + extension);
                    }

                    successCount++;
                    itemResult.put("status", "SUCCESS");
                    itemResult.put("documentId", response.getDocumentId());
                } catch (Exception e) {
                    failCount++;
                    itemResult.put("status", "FAILED");
                    itemResult.put("error", e.getMessage());
                    log.warn("Batch item {} failed: {}", i, e.getMessage());

                    // Check threshold strategy
                    if ("THRESHOLD".equalsIgnoreCase(failureStrategy) && failCount >= failureThreshold) {
                        log.warn("Batch task {} stopped: failure threshold {} reached", taskId, failureThreshold);
                        break;
                    }
                }

                itemResults.add(itemResult);

                // Update progress
                int completed = successCount + failCount;
                task.setCompletedCount(completed);
                task.setSuccessCount(successCount);
                task.setFailCount(failCount);
                task.setProgress(completed * 100 / dataSets.size());
                asyncTaskRepository.save(task);
            }

            // Package successful documents into ZIP
            Long zipDocumentId = null;
            if (!successDocuments.isEmpty()) {
                byte[] zipContent = createZip(successDocuments, documentNames);
                // Store ZIP as a generated document
                GeneratedDocument zipDoc = new GeneratedDocument();
                zipDoc.setTemplateId(templateId);
                zipDoc.setTenantId(template.getTenantId());
                zipDoc.setFormat("ZIP");
                zipDoc.setStatus("COMPLETED");
                zipDoc.setStorageStrategy("TEMP");
                zipDoc.setFileSize((long) zipContent.length);
                zipDoc.setGeneratedAt(Instant.now());

                String zipPath = String.format("batch/%d/%s.zip", templateId, taskId);
                try {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(zipPath)
                            .stream(new ByteArrayInputStream(zipContent), zipContent.length, -1)
                            .contentType("application/zip")
                            .build());
                } catch (Exception e) {
                    log.error("Failed to upload ZIP to MinIO: {}", e.getMessage());
                }

                zipDoc.setFilePath(zipPath);
                zipDoc.setDownloadUrl("/api/tasks/" + taskId + "/download");
                zipDoc = documentRepository.save(zipDoc);
                zipDocumentId = zipDoc.getId();
            }

            // Finalize task
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCompletedCount(successCount + failCount);
            task.setSuccessCount(successCount);
            task.setFailCount(failCount);
            task.setDocumentId(zipDocumentId);
            task.setCompletedAt(Instant.now());
            asyncTaskRepository.save(task);

            log.info("Batch task completed: taskId={}, success={}, fail={}", taskId, successCount, failCount);
        } catch (Exception e) {
            log.error("Batch task failed: taskId={}, error={}", taskId, e.getMessage());
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(Instant.now());
            asyncTaskRepository.save(task);
        }
    }

    /**
     * Create a ZIP file from a list of document byte arrays.
     */
    public byte[] createZip(List<byte[]> documents, List<String> names) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < documents.size(); i++) {
                String name = (i < names.size()) ? names.get(i) : "document_" + (i + 1);
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(documents.get(i));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.GENERATE_FAILED,
                    "Failed to build ZIP archive: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private AsyncTaskDTO toDTO(AsyncTask task) {
        AsyncTaskDTO dto = new AsyncTaskDTO();
        dto.setTaskId(task.getTaskId());
        dto.setTaskType(task.getTaskType());
        dto.setTemplateId(task.getTemplateId());
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress());
        dto.setTotalCount(task.getTotalCount());
        dto.setCompletedCount(task.getCompletedCount());
        dto.setSuccessCount(task.getSuccessCount());
        dto.setFailCount(task.getFailCount());
        dto.setDocumentId(task.getDocumentId());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setCompletedAt(task.getCompletedAt());
        if (task.getDocumentId() != null && "COMPLETED".equals(task.getStatus())) {
            dto.setDownloadUrl("/api/tasks/" + task.getTaskId() + "/download");
        }
        return dto;
    }
}
