CREATE TABLE test_cases (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    test_data_json JSONB NOT NULL,
    expected_result_json JSONB,
    comparison_type VARCHAR(20) NOT NULL DEFAULT 'VARIABLE_VALUE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_test_cases_template_id ON test_cases(template_id);
