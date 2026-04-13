CREATE TABLE scheduled_tasks (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    cron_expression VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    params_json JSONB,
    max_retries INT NOT NULL DEFAULT 3,
    last_execution_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_scheduled_tasks_template_id ON scheduled_tasks(template_id);
CREATE INDEX idx_scheduled_tasks_enabled ON scheduled_tasks(enabled);
