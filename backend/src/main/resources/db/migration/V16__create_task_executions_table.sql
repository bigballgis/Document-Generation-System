CREATE TABLE task_executions (
    id BIGSERIAL PRIMARY KEY,
    scheduled_task_id BIGINT NOT NULL REFERENCES scheduled_tasks(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    document_id BIGINT REFERENCES generated_documents(id),
    error_message TEXT,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_task_executions_scheduled_task_id ON task_executions(scheduled_task_id);
