package com.docgen.repository;

import com.docgen.entity.TemplateVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TemplateVariable} entities.
 */
public interface TemplateVariableRepository extends JpaRepository<TemplateVariable, Long> {

    /**
     * Find all variables belonging to a template, ordered by name ascending.
     */
    List<TemplateVariable> findByTemplateIdOrderByNameAsc(Long templateId);

    /**
     * Delete all variables belonging to a template.
     */
    void deleteByTemplateId(Long templateId);
}
