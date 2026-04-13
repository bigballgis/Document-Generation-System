package com.docgen.repository;

import com.docgen.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link DataSource} entities.
 */
public interface DataSourceRepository extends JpaRepository<DataSource, Long> {

    /**
     * Find all data sources belonging to a template, ordered by priority descending.
     */
    List<DataSource> findByTemplateIdOrderByPriorityDesc(Long templateId);
}
