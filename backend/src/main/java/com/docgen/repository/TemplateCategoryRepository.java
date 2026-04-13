package com.docgen.repository;

import com.docgen.entity.TemplateCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TemplateCategory} entities.
 */
public interface TemplateCategoryRepository extends JpaRepository<TemplateCategory, Long> {

    List<TemplateCategory> findByTenantIdOrderBySortOrderAsc(Long tenantId);

    List<TemplateCategory> findByParentId(Long parentId);

    boolean existsByTenantIdAndName(Long tenantId, String name);
}
