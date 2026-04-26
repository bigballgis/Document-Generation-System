package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.ScheduledTask;
import com.docgen.entity.TaskExecution;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ScheduledTaskRepository;
import com.docgen.repository.TaskExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledTaskServiceTest {

    @Mock
    private ScheduledTaskRepository scheduledTaskRepository;

    @Mock
    private TaskExecutionRepository taskExecutionRepository;

    @Mock
    private DocumentGeneratorService documentGeneratorService;

    private ScheduledTaskService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledTaskService(scheduledTaskRepository,
                taskExecutionRepository, documentGeneratorService);
    }

    // ── createTask ──

    @Test
    void createTask_success() {
        when(scheduledTaskRepository.save(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        CreateScheduledTaskRequest request = new CreateScheduledTaskRequest();
        request.setCronExpression("0 0 * * * *");
        request.setParamsJson("{\"key\":\"value\"}");
        request.setMaxRetries(5);

        ScheduledTaskDTO result = service.createTask(100L, request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(100L, result.getTemplateId());
        assertEquals("0 0 * * * *", result.getCronExpression());
        assertFalse(result.isEnabled());
        assertEquals("{\"key\":\"value\"}", result.getParamsJson());
        assertEquals(5, result.getMaxRetries());

        ArgumentCaptor<ScheduledTask> captor = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(scheduledTaskRepository).save(captor.capture());
        assertEquals(100L, captor.getValue().getTemplateId());
    }

    @Test
    void createTask_invalidCron_throws() {
        CreateScheduledTaskRequest request = new CreateScheduledTaskRequest();
        request.setCronExpression("not-a-cron");

        assertThrows(BusinessException.class, () -> service.createTask(100L, request));
        verify(scheduledTaskRepository, never()).save(any());
    }

    @Test
    void createTask_defaultMaxRetries() {
        when(scheduledTaskRepository.save(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        CreateScheduledTaskRequest request = new CreateScheduledTaskRequest();
        request.setCronExpression("0 0 * * * *");

        ScheduledTaskDTO result = service.createTask(100L, request);
        assertEquals(3, result.getMaxRetries());
    }

    // ── listTasks ──

    @Test
    void listTasks_success() {
        ScheduledTask t1 = createSampleTask(1L, 100L);
        ScheduledTask t2 = createSampleTask(2L, 100L);
        t2.setCronExpression("0 30 * * * *");
        when(scheduledTaskRepository.findByTemplateIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(t1, t2));

        List<ScheduledTaskDTO> result = service.listTasks(100L);

        assertEquals(2, result.size());
        assertEquals("0 0 * * * *", result.get(0).getCronExpression());
        assertEquals("0 30 * * * *", result.get(1).getCronExpression());
    }

    // ── updateTask ──

    @Test
    void updateTask_success() {
        ScheduledTask task = createSampleTask(1L, 100L);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(scheduledTaskRepository.save(any(ScheduledTask.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateScheduledTaskRequest request = new UpdateScheduledTaskRequest();
        request.setCronExpression("0 30 * * * *");
        request.setMaxRetries(10);

        ScheduledTaskDTO result = service.updateTask(1L, request);

        assertEquals("0 30 * * * *", result.getCronExpression());
        assertEquals(10, result.getMaxRetries());
    }

    @Test
    void updateTask_partialUpdate_onlyCron() {
        ScheduledTask task = createSampleTask(1L, 100L);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(scheduledTaskRepository.save(any(ScheduledTask.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateScheduledTaskRequest request = new UpdateScheduledTaskRequest();
        request.setCronExpression("0 15 * * * *");

        ScheduledTaskDTO result = service.updateTask(1L, request);

        assertEquals("0 15 * * * *", result.getCronExpression());
        assertEquals(3, result.getMaxRetries()); // unchanged
    }

    @Test
    void updateTask_invalidCron_throws() {
        ScheduledTask task = createSampleTask(1L, 100L);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        UpdateScheduledTaskRequest request = new UpdateScheduledTaskRequest();
        request.setCronExpression("bad-cron");

        assertThrows(BusinessException.class, () -> service.updateTask(1L, request));
        verify(scheduledTaskRepository, never()).save(any());
    }

    @Test
    void updateTask_notFound_throws() {
        when(scheduledTaskRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateScheduledTaskRequest request = new UpdateScheduledTaskRequest();
        assertThrows(ResourceNotFoundException.class, () -> service.updateTask(999L, request));
    }

    // ── deleteTask ──

    @Test
    void deleteTask_success() {
        ScheduledTask task = createSampleTask(1L, 100L);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        service.deleteTask(1L);

        verify(scheduledTaskRepository).delete(task);
    }

    @Test
    void deleteTask_notFound_throws() {
        when(scheduledTaskRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteTask(999L));
    }

    // ── enableTask / disableTask ──

    @Test
    void enableTask_success() {
        ScheduledTask task = createSampleTask(1L, 100L);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(scheduledTaskRepository.save(any(ScheduledTask.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduledTaskDTO result = service.enableTask(1L);

        assertTrue(result.isEnabled());
    }

    @Test
    void disableTask_success() {
        ScheduledTask task = createSampleTask(1L, 100L);
        task.setEnabled(true);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(scheduledTaskRepository.save(any(ScheduledTask.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduledTaskDTO result = service.disableTask(1L);

        assertFalse(result.isEnabled());
    }

    // ── getExecutionHistory ──

    @Test
    void getExecutionHistory_success() {
        ScheduledTask task = createSampleTask(1L, 100L);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskExecution exec1 = createSampleExecution(10L, 1L, "SUCCESS", 42L);
        TaskExecution exec2 = createSampleExecution(11L, 1L, "FAILED", null);
        exec2.setErrorMessage("Connection timeout");
        Pageable pageable = PageRequest.of(0, 10);
        when(taskExecutionRepository.findByScheduledTaskIdOrderByExecutedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(exec1, exec2)));

        Page<TaskExecutionDTO> result = service.getExecutionHistory(1L, pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("SUCCESS", result.getContent().get(0).getStatus());
        assertEquals(42L, result.getContent().get(0).getDocumentId());
        assertEquals("FAILED", result.getContent().get(1).getStatus());
        assertEquals("Connection timeout", result.getContent().get(1).getErrorMessage());
    }

    @Test
    void getExecutionHistory_taskNotFound_throws() {
        when(scheduledTaskRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getExecutionHistory(999L, PageRequest.of(0, 10)));
    }

    // ── executeTask ──

    @Test
    void executeTask_success() {
        ScheduledTask task = createSampleTask(1L, 100L);
        task.setParamsJson("{\"reportDate\":\"2024-01-01\"}");
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskExecutionRepository.findTopByScheduledTaskIdAndStatusOrderByExecutedAtDesc(1L, "RUNNING"))
                .thenReturn(Optional.empty());
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });

        GenerateDocumentResponse response = new GenerateDocumentResponse();
        response.setDocumentId(42L);
        when(documentGeneratorService.generateDocument(eq(100L), any(GenerateDocumentRequest.class), isNull()))
                .thenReturn(response);

        service.executeTask(1L);

        // Verify execution saved as SUCCESS with document ID
        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, atLeast(2)).save(captor.capture());
        List<TaskExecution> saved = captor.getAllValues();
        // Last save should be the SUCCESS update
        TaskExecution lastSave = saved.get(saved.size() - 1);
        assertEquals("SUCCESS", lastSave.getStatus());
        assertEquals(42L, lastSave.getDocumentId());
    }

    @Test
    void executeTask_skipsWhenPreviousRunning() {
        ScheduledTask task = createSampleTask(1L, 100L);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskExecution running = createSampleExecution(5L, 1L, "RUNNING", null);
        when(taskExecutionRepository.findTopByScheduledTaskIdAndStatusOrderByExecutedAtDesc(1L, "RUNNING"))
                .thenReturn(Optional.of(running));
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> inv.getArgument(0));

        service.executeTask(1L);

        // Should record a SKIPPED execution
        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository).save(captor.capture());
        TaskExecution skipped = captor.getValue();
        assertEquals("SKIPPED", skipped.getStatus());
        assertTrue(skipped.getErrorMessage().contains("previous execution"));
        assertTrue(skipped.getErrorMessage().contains("5"));

        // Should NOT call document generator
        verify(documentGeneratorService, never()).generateDocument(anyLong(), any(), any());
    }

    @Test
    void executeTask_failsAfterRetries() {
        ScheduledTask task = createSampleTask(1L, 100L);
        task.setMaxRetries(2);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskExecutionRepository.findTopByScheduledTaskIdAndStatusOrderByExecutedAtDesc(1L, "RUNNING"))
                .thenReturn(Optional.empty());
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });
        when(scheduledTaskRepository.save(any(ScheduledTask.class))).thenAnswer(inv -> inv.getArgument(0));

        when(documentGeneratorService.generateDocument(eq(100L), any(GenerateDocumentRequest.class), isNull()))
                .thenThrow(new RuntimeException("Service unavailable"));

        service.executeTask(1L);

        // Should attempt 1 initial + 2 retries = 3 total
        verify(documentGeneratorService, times(3)).generateDocument(eq(100L), any(), isNull());

        // Last execution save should be FAILED
        ArgumentCaptor<TaskExecution> captor = ArgumentCaptor.forClass(TaskExecution.class);
        verify(taskExecutionRepository, atLeast(2)).save(captor.capture());
        List<TaskExecution> saved = captor.getAllValues();
        TaskExecution lastSave = saved.get(saved.size() - 1);
        assertEquals("FAILED", lastSave.getStatus());
        assertEquals("Service unavailable", lastSave.getErrorMessage());
    }

    @Test
    void executeTask_nullParams_success() {
        ScheduledTask task = createSampleTask(1L, 100L);
        task.setParamsJson(null);
        when(scheduledTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskExecutionRepository.findTopByScheduledTaskIdAndStatusOrderByExecutedAtDesc(1L, "RUNNING"))
                .thenReturn(Optional.empty());
        when(taskExecutionRepository.save(any(TaskExecution.class))).thenAnswer(inv -> {
            TaskExecution e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });

        GenerateDocumentResponse response = new GenerateDocumentResponse();
        response.setDocumentId(99L);
        when(documentGeneratorService.generateDocument(eq(100L), any(GenerateDocumentRequest.class), isNull()))
                .thenReturn(response);

        service.executeTask(1L);

        verify(documentGeneratorService).generateDocument(eq(100L), any(), isNull());
    }

    // ── Helpers ──

    private ScheduledTask createSampleTask(Long id, Long templateId) {
        ScheduledTask task = new ScheduledTask();
        task.setId(id);
        task.setTemplateId(templateId);
        task.setCronExpression("0 0 * * * *");
        task.setEnabled(false);
        task.setMaxRetries(3);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }

    private TaskExecution createSampleExecution(Long id, Long taskId, String status, Long documentId) {
        TaskExecution exec = new TaskExecution();
        exec.setId(id);
        exec.setScheduledTaskId(taskId);
        exec.setStatus(status);
        exec.setDocumentId(documentId);
        exec.setExecutedAt(Instant.now());
        return exec;
    }
}
