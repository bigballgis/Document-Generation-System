-- V29__create_segments_and_extend_templates.sql
-- 扩展 templates 表支持组合模板，创建 segments 表

-- 1. 扩展 templates 表：添加 template_type 和 assembly_config
ALTER TABLE templates ADD COLUMN template_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE';
ALTER TABLE templates ADD COLUMN assembly_config JSONB;

CREATE INDEX idx_templates_template_type ON templates(template_type);

-- 2. 创建 segments 表
CREATE TABLE segments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    file_path VARCHAR(500) NOT NULL,
    is_component BOOLEAN NOT NULL DEFAULT FALSE,
    segment_type VARCHAR(30),
    created_by BIGINT NOT NULL REFERENCES users(id),
    category_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_segments_tenant_id ON segments(tenant_id);
CREATE INDEX idx_segments_is_component ON segments(is_component);
CREATE INDEX idx_segments_created_by ON segments(created_by);
CREATE INDEX idx_segments_category_id ON segments(category_id);
CREATE INDEX idx_segments_name ON segments(tenant_id, name);
