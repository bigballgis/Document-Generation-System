package com.docgen.repository;

import com.docgen.entity.MarketTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link MarketTemplate} entities.
 * Uses native SQL to avoid Hibernate JPQL parameter type inference issues.
 */
public interface MarketTemplateRepository extends JpaRepository<MarketTemplate, Long> {

    boolean existsByTemplateId(Long templateId);

    Optional<MarketTemplate> findByTemplateId(Long templateId);

    /**
     * Search market templates visible to a specific tenant (GLOBAL or same tenant).
     */
    @Query(value = "SELECT mt.* FROM market_templates mt "
            + "JOIN templates t ON mt.template_id = t.id "
            + "WHERE (mt.share_scope = 'GLOBAL' OR t.tenant_id = :tenantId) "
            + "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery = "SELECT COUNT(*) FROM market_templates mt "
            + "JOIN templates t ON mt.template_id = t.id "
            + "WHERE (mt.share_scope = 'GLOBAL' OR t.tenant_id = :tenantId) "
            + "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            nativeQuery = true)
    Page<MarketTemplate> searchMarketTemplates(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * Search market templates with category filter.
     */
    @Query(value = "SELECT mt.* FROM market_templates mt "
            + "JOIN templates t ON mt.template_id = t.id "
            + "WHERE (mt.share_scope = 'GLOBAL' OR t.tenant_id = :tenantId) "
            + "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categoryId IS NULL OR t.category_id = :categoryId)",
            countQuery = "SELECT COUNT(*) FROM market_templates mt "
            + "JOIN templates t ON mt.template_id = t.id "
            + "WHERE (mt.share_scope = 'GLOBAL' OR t.tenant_id = :tenantId) "
            + "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:categoryId IS NULL OR t.category_id = :categoryId)",
            nativeQuery = true)
    Page<MarketTemplate> searchMarketTemplatesWithCategory(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable);
}
