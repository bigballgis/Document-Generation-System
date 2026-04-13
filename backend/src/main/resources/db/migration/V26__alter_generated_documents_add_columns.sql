-- Add tenant_id, storage_strategy, and download_url columns to generated_documents
ALTER TABLE generated_documents ADD COLUMN tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE generated_documents ADD COLUMN storage_strategy VARCHAR(20) NOT NULL DEFAULT 'TEMP';
ALTER TABLE generated_documents ADD COLUMN download_url VARCHAR(1000);
ALTER TABLE generated_documents ADD COLUMN created_by BIGINT;

CREATE INDEX idx_generated_documents_tenant_id ON generated_documents(tenant_id);
CREATE INDEX idx_generated_documents_status ON generated_documents(status);
CREATE INDEX idx_generated_documents_generated_at ON generated_documents(generated_at);
