CREATE TABLE template_reviews (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    reviewer_id BIGINT NOT NULL REFERENCES users(id),
    review_level INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment TEXT,
    suggestions_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_template_reviews_template_id ON template_reviews(template_id);
CREATE INDEX idx_template_reviews_reviewer_id ON template_reviews(reviewer_id);
CREATE INDEX idx_template_reviews_status ON template_reviews(status);
