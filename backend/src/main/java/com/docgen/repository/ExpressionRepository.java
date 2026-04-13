package com.docgen.repository;

import com.docgen.entity.Expression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Expression} entities.
 */
public interface ExpressionRepository extends JpaRepository<Expression, Long> {

    /**
     * Find all expressions belonging to a template, ordered by execution order ascending.
     */
    List<Expression> findByTemplateIdOrderByExecutionOrderAsc(Long templateId);
}
