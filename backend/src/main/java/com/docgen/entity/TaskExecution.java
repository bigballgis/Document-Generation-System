package com.docgen.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code task_executions} table.
 * Records each execution attempt of a scheduled task, including
 * the outcome status, generated document reference, and any error details.
 */
@Entity
@Table(name = "task_executions")
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheduled_task_id", nullable = false)
    private Long scheduledTaskId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (this.executedAt == null) {
            this.executedAt = Instant.now();
        }
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScheduledTaskId() { return scheduledTaskId; }
    public void setScheduledTaskId(Long scheduledTaskId) { this.scheduledTaskId = scheduledTaskId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}

