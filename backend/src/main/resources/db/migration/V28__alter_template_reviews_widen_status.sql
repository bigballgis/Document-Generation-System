-- Widen status column to accommodate CONDITIONAL_APPROVED (20 chars was too tight)
ALTER TABLE template_reviews ALTER COLUMN status TYPE VARCHAR(30);
