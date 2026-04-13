-- V30__create_segment_versions.sql
-- 创建 segment_versions 表，支持段落独立版本控制

CREATE TABLE segment_versions (
    id BIGSERIAL PRIMARY KEY,
    segment_id BIGINT NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_segment_versions_segment_version UNIQUE (segment_id, version_number)
);

CREATE INDEX idx_segment_versions_segment_id ON segment_versions(segment_id);
