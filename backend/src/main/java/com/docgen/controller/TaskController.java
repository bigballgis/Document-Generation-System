package com.docgen.controller;

import com.docgen.dto.AsyncTaskDTO;
import com.docgen.service.AsyncDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for async task status and progress queries.
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Async Tasks", description = "APIs for querying async task status and downloading results")
public class TaskController {

    private final AsyncDocumentService asyncDocumentService;

    public TaskController(AsyncDocumentService asyncDocumentService) {
        this.asyncDocumentService = asyncDocumentService;
    }

    @Operation(summary = "List tasks", description = "Query async tasks with optional status and templateId filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task list returned")
    })
    @GetMapping
    public ResponseEntity<Page<AsyncTaskDTO>> listTasks(
            @Parameter(description = "Filter by task status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by template ID") @RequestParam(required = false) Long templateId,
            Pageable pageable) {
        return ResponseEntity.ok(asyncDocumentService.listTasks(status, templateId, pageable));
    }

    @Operation(summary = "Get task status", description = "Query the status of an async task by task ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status returned"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping("/{taskId}")
    public ResponseEntity<AsyncTaskDTO> getTaskStatus(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        AsyncTaskDTO task = asyncDocumentService.getTaskStatus(taskId);
        return ResponseEntity.ok(task);
    }

    @Operation(summary = "Get task progress", description = "Query the progress of a batch task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task progress returned"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping("/{taskId}/progress")
    public ResponseEntity<Map<String, Object>> getTaskProgress(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        AsyncTaskDTO task = asyncDocumentService.getTaskStatus(taskId);
        Map<String, Object> progress = Map.of(
                "taskId", task.getTaskId(),
                "status", task.getStatus(),
                "progress", task.getProgress(),
                "totalCount", task.getTotalCount(),
                "completedCount", task.getCompletedCount(),
                "successCount", task.getSuccessCount(),
                "failCount", task.getFailCount()
        );
        return ResponseEntity.ok(progress);
    }

    @Operation(summary = "Download task result",
            description = "Download the generated document or ZIP file for a completed task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document content returned"),
            @ApiResponse(responseCode = "404", description = "Task not found"),
            @ApiResponse(responseCode = "400", description = "Task not completed yet")
    })
    @GetMapping("/{taskId}/download")
    public ResponseEntity<byte[]> downloadTaskResult(
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        return asyncDocumentService.downloadTaskResult(taskId);
    }
}
