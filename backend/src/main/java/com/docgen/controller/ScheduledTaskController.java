package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.service.ScheduledTaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @PostMapping("/api/templates/{templateId}/scheduled-tasks")
    public ResponseEntity<ScheduledTaskDTO> createTask(
            @PathVariable Long templateId,
            @Valid @RequestBody CreateScheduledTaskRequest request) {
        ScheduledTaskDTO dto = scheduledTaskService.createTask(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/api/templates/{templateId}/scheduled-tasks")
    public ResponseEntity<List<ScheduledTaskDTO>> listTasks(
            @PathVariable Long templateId) {
        return ResponseEntity.ok(scheduledTaskService.listTasks(templateId));
    }

    @PutMapping("/api/scheduled-tasks/{taskId}")
    public ResponseEntity<ScheduledTaskDTO> updateTask(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateScheduledTaskRequest request) {
        return ResponseEntity.ok(scheduledTaskService.updateTask(taskId, request));
    }

    @DeleteMapping("/api/scheduled-tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long taskId) {
        scheduledTaskService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/scheduled-tasks/{taskId}/enable")
    public ResponseEntity<ScheduledTaskDTO> enableTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(scheduledTaskService.enableTask(taskId));
    }

    @PutMapping("/api/scheduled-tasks/{taskId}/disable")
    public ResponseEntity<ScheduledTaskDTO> disableTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(scheduledTaskService.disableTask(taskId));
    }

    @GetMapping("/api/scheduled-tasks/{taskId}/executions")
    public ResponseEntity<Page<TaskExecutionDTO>> getExecutionHistory(
            @PathVariable Long taskId,
            Pageable pageable) {
        return ResponseEntity.ok(scheduledTaskService.getExecutionHistory(taskId, pageable));
    }
}
