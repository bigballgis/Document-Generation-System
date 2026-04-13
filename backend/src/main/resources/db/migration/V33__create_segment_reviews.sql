-- V33__create_segment_reviews.sql
-- 创建 segment_reviews 表，支持段落级独立审查

CREATE TABLE segment_reviews (
    id BIGSERIAL PRIMARY KEY,
    template_review_id BIGINT NOT NULL REFERENCES template_reviews(id) ON DELETE CASCADE,
    segment_id BIGINT NOT NULL REFERENCES segments(id),
    reviewer_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_segment_reviews_template_review_id ON segment_reviews(template_review_id);
CREATE INDEX idx_segment_reviews_segment_id ON segment_reviews(segment_id);
CREATE INDEX idx_segment_reviews_reviewer_id ON segment_reviews(reviewer_id);
