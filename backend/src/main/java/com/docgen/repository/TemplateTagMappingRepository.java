package com.docgen.repository;

import com.docgen.entity.TemplateTagMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TemplateTagMapping} entities.
 */
public interface TemplateTagMappingRepository extends JpaRepository<TemplateTagMapping, TemplateTagMapping.TemplateTagMappingId> {

    List<TemplateTagMapping> findByTemplateId(Long templateId);

    List<TemplateTagMapping> findByTagId(Long tagId);

    void deleteByTemplateIdAndTagId(Long templateId, Long tagId);

    boolean existsByTemplateIdAndTagId(Long templateId, Long tagId);

    @Query("SELECT m.templateId FROM TemplateTagMapping m WHERE m.tagId IN :tagIds GROUP BY m.templateId HAVING COUNT(DISTINCT m.tagId) = :tagCount")
    List<Long> findTemplateIdsHavingAllTags(@Param("tagIds") List<Long> tagIds, @Param("tagCount") long tagCount);
}
