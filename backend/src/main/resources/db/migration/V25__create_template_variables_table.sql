CREATE TABLE template_variables (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    variable_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    default_value TEXT,
    description TEXT,
    binding_source VARCHAR(20),
    binding_field VARCHAR(200),
    is_bound BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_template_variables_template_id ON template_variables(template_id);
