CREATE TABLE rate_limit_configs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) UNIQUE,
    monthly_quota BIGINT NOT NULL DEFAULT 100000,
    current_month_usage BIGINT NOT NULL DEFAULT 0,
    reset_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_rate_limit_configs_tenant_id ON rate_limit_configs(tenant_id);
