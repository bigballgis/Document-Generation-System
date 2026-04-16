-- V38: Drop legacy DataSource, Expression, and TemplateVariable tables
-- These tables are replaced by the unified template_parameters table (V37).

DROP TABLE IF EXISTS data_sources CASCADE;
DROP TABLE IF EXISTS expressions CASCADE;
DROP TABLE IF EXISTS template_variables CASCADE;
