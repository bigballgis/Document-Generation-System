-- V39__create_segment_versions_inline.sql
-- 创建内联片段版本表，支持片段级别的发布和版本比较

CREATE TABLE segment_versions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    segment_name VARCHAR(200) NOT NULL,
    version_number INTEGER NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    segment_type VARCHAR(50),
    config_snapshot JSONB,
    comment TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_segment_versions_template_name_version UNIQUE (template_id, segment_name, version_number)
);

CREATE INDEX idx_segment_versions_template_id ON segment_versions(template_id);
CREATE INDEX idx_segment_versions_tenant_id ON segment_versions(tenant_id);
CREATE INDEX idx_segment_versions_template_name ON segment_versions(template_id, segment_name);
