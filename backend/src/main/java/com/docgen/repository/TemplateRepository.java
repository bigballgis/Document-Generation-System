package com.docgen.repository;

import com.docgen.entity.Template;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Template} entities.
 * Uses native SQL queries to avoid Hibernate JPQL parameter type inference issues with PostgreSQL.
 */
public interface TemplateRepository extends JpaRepository<Template, Long> {

    /**
     * Fuzzy search by name and/or description with pagination.
     * Native query avoids Hibernate inferring bytea for null keyword.
     */
    @Query(value = "SELECT * FROM templates t WHERE "
            + "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery = "SELECT COUNT(*) FROM templates t WHERE "
            + "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            nativeQuery = true)
    Page<Template> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Find templates by category ID.
     */
    List<Template> findByCategoryId(Long categoryId);

    long countByStatus(String status);

    long countByTemplateType(String templateType);

    /**
     * Search templates with optional keyword and category filter.
     */
    @Query(value = "SELECT * FROM templates t WHERE "
            + "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categoryId IS NULL OR t.category_id = :categoryId)",
            countQuery = "SELECT COUNT(*) FROM templates t WHERE "
            + "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categoryId IS NULL OR t.category_id = :categoryId)",
            nativeQuery = true)
    Page<Template> searchByKeywordAndCategory(@Param("keyword") String keyword,
                                               @Param("categoryId") Long categoryId,
                                               Pageable pageable);

    /**
     * Search templates by keyword, category, and restricted to specific IDs (for tag filtering).
     */
    @Query(value = "SELECT * FROM templates t WHERE "
            + "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categoryId IS NULL OR t.category_id = :categoryId) "
            + "AND t.id IN (:templateIds)",
            countQuery = "SELECT COUNT(*) FROM templates t WHERE "
            + "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categoryId IS NULL OR t.category_id = :categoryId) "
            + "AND t.id IN (:templateIds)",
            nativeQuery = true)
    Page<Template> searchByKeywordAndCategoryAndIds(@Param("keyword") String keyword,
                                                     @Param("categoryId") Long categoryId,
                                                     @Param("templateIds") List<Long> templateIds,
                                                     Pageable pageable);

    /**
     * Find all COMPOSITE templates whose assembly_config JSONB contains a reference
     * to the given segmentId. Uses PostgreSQL JSONB containment via a cast to text
     * with a LIKE match on the segmentId pattern inside the JSON structure.
     */
    @Query(value = "SELECT * FROM templates t WHERE t.template_type = 'COMPOSITE' "
            + "AND t.assembly_config IS NOT NULL "
            + "AND t.assembly_config::text LIKE CONCAT('%\"segmentId\":', CAST(:segmentId AS TEXT), '%')",
            nativeQuery = true)
    List<Template> findCompositeTemplatesReferencingSegment(@Param("segmentId") Long segmentId);
}
