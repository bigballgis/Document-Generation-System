-- V37__create_template_parameters_and_migrate_data.sql
-- Creates the unified template_parameters table and migrates data from
-- template_variables and expressions. Old tables are NOT dropped here;
-- they will be dropped in V38 after Java code removal.

-- ============================================================
-- Step 1: Create template_parameters table
-- ============================================================
CREATE TABLE template_parameters (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    parent_id       BIGINT REFERENCES template_parameters(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    parameter_type  VARCHAR(20) NOT NULL DEFAULT 'REQUEST',
    data_type       VARCHAR(20) NOT NULL DEFAULT 'STRING',
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    default_value   TEXT,
    description     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    expression_text TEXT,
    expression_type VARCHAR(20),
    validation_rules JSONB,
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Unique name within the same parent scope (non-NULL parent_id)
    CONSTRAINT uq_template_parameters_scope_name
        UNIQUE (template_id, parent_id, name),

    CONSTRAINT chk_parameter_type
        CHECK (parameter_type IN ('REQUEST', 'DERIVED')),

    CONSTRAINT chk_data_type
        CHECK (data_type IN ('STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT')),

    -- DERIVED parameters must have a non-empty expression_text
    CONSTRAINT chk_derived_expression
        CHECK (parameter_type != 'DERIVED' OR (expression_text IS NOT NULL AND expression_text != '')),

    -- REQUEST parameters must not have expression_text
    CONSTRAINT chk_request_no_expression
        CHECK (parameter_type != 'REQUEST' OR expression_text IS NULL),

    CONSTRAINT chk_expression_type
        CHECK (expression_type IS NULL OR expression_type IN ('JAVASCRIPT', 'EXCEL_FORMULA')),

    CONSTRAINT chk_name_pattern
        CHECK (name ~ '^[a-zA-Z_][a-zA-Z0-9_-]*$')
);

-- Indexes
CREATE INDEX idx_template_parameters_template_id ON template_parameters(template_id);
CREATE INDEX idx_template_parameters_parent_id ON template_parameters(parent_id);

-- Partial unique index for root-level name uniqueness (parent_id IS NULL).
-- PostgreSQL UNIQUE constraints treat NULLs as distinct, so the composite
-- unique constraint above does not prevent two root parameters with the
-- same (template_id, name). This partial index fills that gap.
CREATE UNIQUE INDEX uq_template_parameters_root_name
    ON template_parameters(template_id, name) WHERE parent_id IS NULL;

-- ============================================================
-- Step 2: Migrate TemplateVariable → REQUEST parameters
-- Variables that are NOT bound to an expression become REQUEST params.
-- ============================================================
INSERT INTO template_parameters (
    template_id, name, parameter_type, data_type,
    required, default_value, description, sort_order,
    created_at, updated_at
)
SELECT
    tv.template_id,
    tv.name,
    'REQUEST',
    CASE tv.variable_type
        WHEN 'STRING'  THEN 'STRING'
        WHEN 'NUMBER'  THEN 'NUMBER'
        WHEN 'DATE'    THEN 'DATE'
        WHEN 'BOOLEAN' THEN 'BOOLEAN'
        ELSE 'STRING'
    END,
    FALSE,
    tv.default_value,
    tv.description,
    0,
    tv.created_at,
    NOW()
FROM template_variables tv
WHERE tv.binding_source IS NULL
   OR tv.binding_source != 'EXPRESSION';

-- ============================================================
-- Step 3: Migrate Expression-bound TemplateVariable → DERIVED parameters
-- Variables with binding_source = 'EXPRESSION' are joined with the
-- corresponding expression record to carry over expression metadata.
-- ============================================================
INSERT INTO template_parameters (
    template_id, name, parameter_type, data_type,
    default_value, description, sort_order,
    expression_text, expression_type,
    created_at, updated_at
)
SELECT
    tv.template_id,
    tv.name,
    'DERIVED',
    'STRING',
    tv.default_value,
    tv.description,
    e.execution_order,
    e.expression_text,
    e.expression_type,
    tv.created_at,
    NOW()
FROM template_variables tv
JOIN expressions e
    ON e.template_id = tv.template_id
   AND e.name = tv.binding_field
WHERE tv.binding_source = 'EXPRESSION';

-- ============================================================
-- Step 4: Migrate standalone Expressions → DERIVED parameters
-- Expressions that are NOT referenced by any TemplateVariable are
-- migrated as independent DERIVED parameters.
-- ============================================================
INSERT INTO template_parameters (
    template_id, name, parameter_type, data_type,
    description, sort_order,
    expression_text, expression_type,
    created_at, updated_at
)
SELECT
    e.template_id,
    e.name,
    'DERIVED',
    'STRING',
    e.description,
    e.execution_order,
    e.expression_text,
    e.expression_type,
    e.created_at,
    NOW()
FROM expressions e
WHERE NOT EXISTS (
    SELECT 1
    FROM template_variables tv
    WHERE tv.template_id = e.template_id
      AND tv.binding_source = 'EXPRESSION'
      AND tv.binding_field = e.name
);

-- ============================================================
-- NOTE: Old tables (data_sources, expressions, template_variables)
-- are intentionally NOT dropped in this migration. They will be
-- dropped in V38 after the Java code that references them has been
-- removed in the backend-cleanup sub-spec.
-- ============================================================
