-- Add allow_history_versions column to templates for API version control
ALTER TABLE templates ADD COLUMN allow_history_versions BOOLEAN NOT NULL DEFAULT TRUE;

-- Add tenant_id to async_tasks for tenant isolation
ALTER TABLE async_tasks ADD COLUMN tenant_id BIGINT REFERENCES tenants(id);

-- Add total_count and completed_count for batch progress tracking
ALTER TABLE async_tasks ADD COLUMN total_count INT NOT NULL DEFAULT 0;
ALTER TABLE async_tasks ADD COLUMN completed_count INT NOT NULL DEFAULT 0;
ALTER TABLE async_tasks ADD COLUMN success_count INT NOT NULL DEFAULT 0;
ALTER TABLE async_tasks ADD COLUMN fail_count INT NOT NULL DEFAULT 0;

-- Add document_id for single async task result
ALTER TABLE async_tasks ADD COLUMN document_id BIGINT REFERENCES generated_documents(id);

-- Add index for tenant isolation
CREATE INDEX idx_async_tasks_tenant_id ON async_tasks(tenant_id);
CREATE INDEX idx_async_tasks_template_id ON async_tasks(template_id);
