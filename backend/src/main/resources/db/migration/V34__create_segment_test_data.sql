-- V34__create_segment_test_data.sql
-- 创建 segment_test_data 表，支持段落级测试数据管理

CREATE TABLE segment_test_data (
    id BIGSERIAL PRIMARY KEY,
    segment_id BIGINT NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    test_data_json JSONB NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_segment_test_data_segment_id ON segment_test_data(segment_id);
