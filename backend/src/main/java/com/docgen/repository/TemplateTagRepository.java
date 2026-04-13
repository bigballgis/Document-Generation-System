package com.docgen.repository;

import com.docgen.entity.TemplateTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TemplateTag} entities.
 */
public interface TemplateTagRepository extends JpaRepository<TemplateTag, Long> {

    List<TemplateTag> findByTenantId(Long tenantId);

    boolean existsByTenantIdAndName(Long tenantId, String name);
}
