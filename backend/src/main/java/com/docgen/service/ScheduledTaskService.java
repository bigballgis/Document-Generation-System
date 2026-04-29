package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.ScheduledTask;
import com.docgen.entity.TaskExecution;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ScheduledTaskRepository;
import com.docgen.repository.TaskExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service managing scheduled document generation tasks.
 * Supports CRUD operations, enable/disable, execution with retry,
 * skip-if-running logic, and execution history tracking.
 */
@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";

    private final ScheduledTaskRepository scheduledTaskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final DocumentGeneratorService documentGeneratorService;

    public ScheduledTaskService(ScheduledTaskRepository scheduledTaskRepository,
                                TaskExecutionRepository taskExecutionRepository,
                                DocumentGeneratorService documentGeneratorService) {
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.taskExecutionRepository = taskExecutionRepository;
        this.documentGeneratorService = documentGeneratorService;
    }


    @Transactional
    public ScheduledTaskDTO createTask(Long templateId, CreateScheduledTaskRequest request) {
        validateCronExpression(request.getCronExpression());

        ScheduledTask task = new ScheduledTask();
        task.setTemplateId(templateId);
        task.setCronExpression(request.getCronExpression());
        task.setParamsJson(request.getParamsJson());
        if (request.getMaxRetries() != null) {
            task.setMaxRetries(request.getMaxRetries());
        }
        task = scheduledTaskRepository.save(task);
        return toDTO(task);
    }

    @Transactional(readOnly = true)
    public List<ScheduledTaskDTO> listTasks(Long templateId) {
        return scheduledTaskRepository.findByTemplateIdOrderByCreatedAtDesc(templateId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public ScheduledTaskDTO updateTask(Long taskId, UpdateScheduledTaskRequest request) {
        ScheduledTask task = findTaskOrThrow(taskId);

        if (request.getCronExpression() != null) {
            validateCronExpression(request.getCronExpression());
            task.setCronExpression(request.getCronExpression());
        }
        if (request.getParamsJson() != null) {
            task.setParamsJson(request.getParamsJson());
        }
        if (request.getMaxRetries() != null) {
            task.setMaxRetries(request.getMaxRetries());
        }
        task = scheduledTaskRepository.save(task);
        return toDTO(task);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        ScheduledTask task = findTaskOrThrow(taskId);
        scheduledTaskRepository.delete(task);
    }

    @Transactional
    public ScheduledTaskDTO enableTask(Long taskId) {
        ScheduledTask task = findTaskOrThrow(taskId);
        task.setEnabled(true);
        task = scheduledTaskRepository.save(task);
        return toDTO(task);
    }

    @Transactional
    public ScheduledTaskDTO disableTask(Long taskId) {
        ScheduledTask task = findTaskOrThrow(taskId);
        task.setEnabled(false);
        task = scheduledTaskRepository.save(task);
        return toDTO(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskExecutionDTO> getExecutionHistory(Long taskId, Pageable pageable) {
        findTaskOrThrow(taskId);
        return taskExecutionRepository.findByScheduledTaskIdOrderByExecutedAtDesc(taskId, pageable)
                .map(this::toExecutionDTO);
    }


    /**
     * Execute a scheduled task. If the previous execution is still running,
     * skip this trigger and record a SKIPPED log entry.
     * On failure, retry up to {@code maxRetries} times.
     */
    @Transactional
    public void executeTask(Long taskId) {
        ScheduledTask task = findTaskOrThrow(taskId);

        // Check if previous execution is still running
        Optional<TaskExecution> runningExecution = taskExecutionRepository
                .findTopByScheduledTaskIdAndStatusOrderByExecutedAtDesc(taskId, STATUS_RUNNING);

        if (runningExecution.isPresent()) {
            log.warn("Skipping scheduled task {} — previous execution {} is still running",
                    taskId, runningExecution.get().getId());
            TaskExecution skipped = new TaskExecution();
            skipped.setScheduledTaskId(taskId);
            skipped.setStatus(STATUS_SKIPPED);
            skipped.setErrorMessage("Skipped: previous execution " + runningExecution.get().getId()
                    + " is still running");
            taskExecutionRepository.save(skipped);
            return;
        }

        // Create a RUNNING execution record
        TaskExecution execution = new TaskExecution();
        execution.setScheduledTaskId(taskId);
        execution.setStatus(STATUS_RUNNING);
        execution = taskExecutionRepository.save(execution);

        int attempts = 0;
        int maxRetries = task.getMaxRetries();
        Exception lastException = null;

        while (attempts <= maxRetries) {
            try {
                GenerateDocumentRequest request = new GenerateDocumentRequest();
                if (task.getParamsJson() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(task.getParamsJson(), Map.class);
                    request.setParameters(params);
                }

                GenerateDocumentResponse response = documentGeneratorService
                        .generateDocument(task.getTemplateId(), request, null);

                // Success
                execution.setStatus(STATUS_SUCCESS);
                execution.setDocumentId(response.getDocumentId());
                taskExecutionRepository.save(execution);

                task.setLastExecutionAt(Instant.now());
                scheduledTaskRepository.save(task);

                log.info("Scheduled task {} executed successfully, document {}",
                        taskId, response.getDocumentId());
                return;

            } catch (Exception e) {
                lastException = e;
                attempts++;
                log.warn("Scheduled task {} attempt {}/{} failed: {}",
                        taskId, attempts, maxRetries + 1, e.getMessage());
            }
        }

        // All retries exhausted — record failure
        execution.setStatus(STATUS_FAILED);
        execution.setErrorMessage(lastException != null ? lastException.getMessage() : "Unknown error");
        taskExecutionRepository.save(execution);

        task.setLastExecutionAt(Instant.now());
        scheduledTaskRepository.save(task);

        log.error("Scheduled task {} failed after {} attempts: {}",
                taskId, maxRetries + 1,
                lastException != null ? lastException.getMessage() : "Unknown error");
    }


    private void validateCronExpression(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SCHEDULED_TASK_INVALID_CRON,
                    "Invalid cron expression: " + cron, HttpStatus.BAD_REQUEST);
        }
    }

    private ScheduledTask findTaskOrThrow(Long taskId) {
        return scheduledTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SCHEDULED_TASK_NOT_FOUND,
                        "Scheduled task not found: " + taskId));
    }

    private ScheduledTaskDTO toDTO(ScheduledTask entity) {
        ScheduledTaskDTO dto = new ScheduledTaskDTO();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setCronExpression(entity.getCronExpression());
        dto.setEnabled(entity.isEnabled());
        dto.setParamsJson(entity.getParamsJson());
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setLastExecutionAt(entity.getLastExecutionAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private TaskExecutionDTO toExecutionDTO(TaskExecution entity) {
        TaskExecutionDTO dto = new TaskExecutionDTO();
        dto.setId(entity.getId());
        dto.setScheduledTaskId(entity.getScheduledTaskId());
        dto.setStatus(entity.getStatus());
        dto.setDocumentId(entity.getDocumentId());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setExecutedAt(entity.getExecutedAt());
        return dto;
    }
}

