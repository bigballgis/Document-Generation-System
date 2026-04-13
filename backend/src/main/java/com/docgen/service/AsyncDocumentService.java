package com.docgen.service;

import com.docgen.dto.AsyncTaskDTO;
import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.entity.AsyncTask;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.AsyncTaskRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for asynchronous document generation.
 * Creates tasks that are processed in the background.
 */
@Service
public class AsyncDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AsyncDocumentService.class);

    private final AsyncTaskRepository asyncTaskRepository;
    private final TemplateRepository templateRepository;
    private final DocumentGeneratorService documentGeneratorService;
    private final DocumentStorageService documentStorageService;

    public AsyncDocumentService(AsyncTaskRepository asyncTaskRepository,
                                TemplateRepository templateRepository,
                                DocumentGeneratorService documentGeneratorService,
                                DocumentStorageService documentStorageService) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.templateRepository = templateRepository;
        this.documentGeneratorService = documentGeneratorService;
        this.documentStorageService = documentStorageService;
    }

    /**
     * Submit an async document generation task.
     * Returns immediately with a task ID.
     */
    @Transactional
    public AsyncTaskDTO submitAsyncGeneration(Long templateId, GenerateDocumentRequest request) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        AsyncTask task = new AsyncTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType("SINGLE_GENERATE");
        task.setTemplateId(templateId);
        task.setTenantId(template.getTenantId());
        task.setStatus("PENDING");
        task.setTotalCount(1);
        task = asyncTaskRepository.save(task);

        // Trigger async processing
        processAsyncGeneration(task.getTaskId(), templateId, request);

        return toDTO(task);
    }

    /**
     * Process the async generation in the background.
     */
    @Async
    public void processAsyncGeneration(String taskId, Long templateId, GenerateDocumentRequest request) {
        AsyncTask task = asyncTaskRepository.findByTaskId(taskId).orElse(null);
        if (task == null) return;

        try {
            task.setStatus("RUNNING");
            asyncTaskRepository.save(task);

            GenerateDocumentResponse response = documentGeneratorService.generateDocument(templateId, request);

            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCompletedCount(1);
            task.setSuccessCount(1);
            task.setDocumentId(response.getDocumentId());
            task.setCompletedAt(Instant.now());
            if (response.getDownloadUrl() != null) {
                task.setResultData("{\"downloadUrl\":\"" + response.getDownloadUrl() + "\"}");
            }
            asyncTaskRepository.save(task);

            log.info("Async task completed: taskId={}, documentId={}", taskId, response.getDocumentId());
        } catch (Exception e) {
            log.error("Async task failed: taskId={}, error={}", taskId, e.getMessage());
            task.setStatus("FAILED");
            task.setFailCount(1);
            task.setCompletedCount(1);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(Instant.now());
            asyncTaskRepository.save(task);
        }
    }

    /**
     * List async tasks for the current tenant with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<AsyncTaskDTO> listTasks(String status, Long templateId, Pageable pageable) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return asyncTaskRepository.findByFilters(tenantId, status, templateId, pageable)
                .map(this::toDTO);
    }

    /**
     * Get the status of an async task.
     */
    @Transactional(readOnly = true)
    public AsyncTaskDTO getTaskStatus(String taskId) {
        AsyncTask task = asyncTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                        "任务不存在: " + taskId, HttpStatus.NOT_FOUND));
        return toDTO(task);
    }

    /**
     * Download the result of a completed task.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadTaskResult(String taskId) {
        AsyncTask task = asyncTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                        "任务不存在: " + taskId, HttpStatus.NOT_FOUND));

        if (!"COMPLETED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_NOT_COMPLETED,
                    "任务尚未完成: " + taskId, HttpStatus.BAD_REQUEST);
        }

        if ("BATCH_GENERATE".equals(task.getTaskType())) {
            // For batch tasks, download the ZIP file
            if (task.getDocumentId() != null) {
                byte[] content = documentStorageService.downloadDocument(task.getDocumentId());
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"batch_" + taskId + ".zip\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(content);
            }
        } else {
            // For single tasks, download the document
            if (task.getDocumentId() != null) {
                byte[] content = documentStorageService.downloadDocument(task.getDocumentId());
                String contentType = documentStorageService.getContentType(task.getDocumentId());
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"document_" + taskId + "\"")
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(content);
            }
        }

        throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND,
                "任务结果文档不存在", HttpStatus.NOT_FOUND);
    }

    AsyncTaskDTO toDTO(AsyncTask task) {
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
