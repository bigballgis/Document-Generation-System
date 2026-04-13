CREATE TABLE template_categories (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    parent_id BIGINT REFERENCES template_categories(id),
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_template_categories_tenant_id ON template_categories(tenant_id);
CREATE INDEX idx_template_categories_parent_id ON template_categories(parent_id);

-- Add FK constraint to templates.category_id now that the table exists
ALTER TABLE templates ADD CONSTRAINT fk_templates_category_id FOREIGN KEY (category_id) REFERENCES template_categories(id);
