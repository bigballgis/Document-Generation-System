-- Optional post-assembly render settings (watermark, future barcode) for composite and single templates.
-- Stored as JSON; validated on composite ZIP import per REQ-R7-001.
ALTER TABLE templates ADD COLUMN render_config JSONB;
