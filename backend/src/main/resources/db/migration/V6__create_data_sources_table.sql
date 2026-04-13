CREATE TABLE data_sources (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    config_json JSONB NOT NULL,
    cache_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cache_ttl INT DEFAULT 300,
    priority INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_data_sources_template_id ON data_sources(template_id);
