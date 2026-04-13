CREATE TABLE templates (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    template_file_path VARCHAR(500) NOT NULL,
    output_format VARCHAR(20) NOT NULL DEFAULT 'WORD',
    storage_strategy VARCHAR(20) NOT NULL DEFAULT 'TEMP',
    is_async BOOLEAN NOT NULL DEFAULT FALSE,
    team_id BIGINT REFERENCES teams(id),
    created_by BIGINT NOT NULL REFERENCES users(id),
    category_id BIGINT,
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_templates_tenant_id ON templates(tenant_id);
CREATE INDEX idx_templates_team_id ON templates(team_id);
CREATE INDEX idx_templates_created_by ON templates(created_by);
CREATE INDEX idx_templates_status ON templates(status);
CREATE INDEX idx_templates_category_id ON templates(category_id);
