CREATE TABLE template_tag_mappings (
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES template_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (template_id, tag_id)
);
