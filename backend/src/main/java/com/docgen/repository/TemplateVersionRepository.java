package com.docgen.repository;

import com.docgen.entity.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TemplateVersion} entities.
 */
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    /**
     * Find all versions for a template, ordered by version number descending (newest first).
     */
    List<TemplateVersion> findByTemplateIdOrderByVersionNumberDesc(Long templateId);

    /**
     * Find the maximum version number for a given template.
     * Returns empty if no versions exist yet.
     */
    @Query("SELECT MAX(v.versionNumber) FROM TemplateVersion v WHERE v.templateId = :templateId")
    Optional<Integer> findMaxVersionNumber(@Param("templateId") Long templateId);

    /**
     * Find a specific version by template ID and version ID.
     */
    Optional<TemplateVersion> findByIdAndTemplateId(Long id, Long templateId);

    /**
     * Find a specific version by template ID and version number.
     */
    Optional<TemplateVersion> findByTemplateIdAndVersionNumber(Long templateId, Integer versionNumber);
}
