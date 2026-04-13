CREATE TABLE webhook_logs (
    id BIGSERIAL PRIMARY KEY,
    webhook_config_id BIGINT NOT NULL REFERENCES webhook_configs(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    response_status INT,
    response_body TEXT,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_webhook_logs_webhook_config_id ON webhook_logs(webhook_config_id);
CREATE INDEX idx_webhook_logs_sent_at ON webhook_logs(sent_at);
