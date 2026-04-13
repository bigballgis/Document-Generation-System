-- V32__extend_permissions_for_segments.sql
-- 扩展 permissions 表支持段落级权限控制

-- 添加 resource_type 和 resource_id 列，支持多资源类型权限
ALTER TABLE permissions ADD COLUMN resource_type VARCHAR(20) NOT NULL DEFAULT 'TEMPLATE';
ALTER TABLE permissions ADD COLUMN resource_id BIGINT;

-- 将现有 template_id 数据迁移到 resource_id
UPDATE permissions SET resource_id = template_id WHERE resource_type = 'TEMPLATE';

CREATE INDEX idx_permissions_resource ON permissions(resource_type, resource_id);
