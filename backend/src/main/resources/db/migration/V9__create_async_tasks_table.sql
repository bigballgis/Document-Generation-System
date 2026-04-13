CREATE TABLE async_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(50) NOT NULL UNIQUE,
    task_type VARCHAR(20) NOT NULL,
    template_id BIGINT NOT NULL REFERENCES templates(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INT NOT NULL DEFAULT 0,
    error_message TEXT,
    result_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_async_tasks_task_id ON async_tasks(task_id);
CREATE INDEX idx_async_tasks_status ON async_tasks(status);
