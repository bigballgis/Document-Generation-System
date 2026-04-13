CREATE TABLE template_versions (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    template_file_path VARCHAR(500) NOT NULL,
    config_json JSONB NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(template_id, version_number)
);
CREATE INDEX idx_template_versions_template_id ON template_versions(template_id);
