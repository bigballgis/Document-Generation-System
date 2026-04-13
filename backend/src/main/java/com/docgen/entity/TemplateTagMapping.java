package com.docgen.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * JPA Entity mapping to the {@code template_tag_mappings} join table.
 * Uses a composite primary key of (templateId, tagId).
 */
@Entity
@Table(name = "template_tag_mappings")
@IdClass(TemplateTagMapping.TemplateTagMappingId.class)
public class TemplateTagMapping {

    @Id
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    public TemplateTagMapping() {}

    public TemplateTagMapping(Long templateId, Long tagId) {
        this.templateId = templateId;
        this.tagId = tagId;
    }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    /**
     * Composite primary key for TemplateTagMapping.
     */
    public static class TemplateTagMappingId implements Serializable {
        private Long templateId;
        private Long tagId;

        public TemplateTagMappingId() {}

        public TemplateTagMappingId(Long templateId, Long tagId) {
            this.templateId = templateId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TemplateTagMappingId that = (TemplateTagMappingId) o;
            return Objects.equals(templateId, that.templateId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(templateId, tagId);
        }
    }
}
