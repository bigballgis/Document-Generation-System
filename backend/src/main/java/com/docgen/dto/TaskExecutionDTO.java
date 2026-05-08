package com.docgen.dto;

import java.time.Instant;

public class TaskExecutionDTO {

    private Long id;
    private Long scheduledTaskId;
    private String status;
    private Long documentId;
    private String errorMessage;
    private Instant executedAt;

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
