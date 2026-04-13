-- V35__create_segment_favorites.sql
-- 创建 segment_favorites 表，支持段落收藏功能

CREATE TABLE segment_favorites (
    id BIGSERIAL PRIMARY KEY,
    segment_id BIGINT NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_segment_favorites UNIQUE (segment_id, user_id)
);

CREATE INDEX idx_segment_favorites_user_id ON segment_favorites(user_id);
