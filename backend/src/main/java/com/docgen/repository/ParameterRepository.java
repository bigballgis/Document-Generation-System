package com.docgen.repository;

import com.docgen.entity.ParameterDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ParameterDefinition} entities.
 */
public interface ParameterRepository extends JpaRepository<ParameterDefinition, Long> {

    /**
     * Find all parameters belonging to a template, ordered by sort_order ascending.
     */
    List<ParameterDefinition> findByTemplateIdOrderBySortOrderAsc(Long templateId);

    /**
     * Find all child parameters of a given parent, ordered by sort_order ascending.
     */
    List<ParameterDefinition> findByParentIdOrderBySortOrderAsc(Long parentId);

    /**
     * Find all root-level parameters (parent_id IS NULL) for a template, ordered by sort_order ascending.
     */
    List<ParameterDefinition> findByTemplateIdAndParentIdIsNullOrderBySortOrderAsc(Long templateId);

    /**
     * Count child parameters under a given parent within a template.
     * Useful for depth calculation and tree operations.
     */
    long countByTemplateIdAndParentId(Long templateId, Long parentId);

    /**
     * Check if a parameter with the given name exists under the same parent scope.
     * Used for duplicate name validation (non-root parameters).
     */
    boolean existsByTemplateIdAndParentIdAndName(Long templateId, Long parentId, String name);

    /**
     * Check if a root-level parameter with the given name exists.
     * Used for duplicate name validation (root parameters where parent_id IS NULL).
     */
    boolean existsByTemplateIdAndParentIdIsNullAndName(Long templateId, String name);
}
