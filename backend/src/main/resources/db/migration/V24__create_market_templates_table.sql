CREATE TABLE market_templates (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id),
    shared_by BIGINT NOT NULL REFERENCES users(id),
    share_scope VARCHAR(20) NOT NULL,
    usage_count INT NOT NULL DEFAULT 0,
    rating DECIMAL(3,2) DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_market_templates_template_id ON market_templates(template_id);
CREATE INDEX idx_market_templates_share_scope ON market_templates(share_scope);
