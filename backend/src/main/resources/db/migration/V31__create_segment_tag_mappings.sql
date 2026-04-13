-- V31__create_segment_tag_mappings.sql
-- 创建 segment_tag_mappings 表，复用现有 template_tags 体系

CREATE TABLE segment_tag_mappings (
    id BIGSERIAL PRIMARY KEY,
    segment_id BIGINT NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES template_tags(id) ON DELETE CASCADE,
    CONSTRAINT uq_segment_tag_mapping UNIQUE (segment_id, tag_id)
);

CREATE INDEX idx_segment_tag_mappings_segment_id ON segment_tag_mappings(segment_id);
CREATE INDEX idx_segment_tag_mappings_tag_id ON segment_tag_mappings(tag_id);
