CREATE TABLE expressions (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    expression_type VARCHAR(20) NOT NULL,
    expression_text TEXT NOT NULL,
    description TEXT,
    execution_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_expressions_template_id ON expressions(template_id);
