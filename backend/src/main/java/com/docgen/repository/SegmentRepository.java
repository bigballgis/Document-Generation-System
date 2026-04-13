package com.docgen.repository;

import com.docgen.entity.Segment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Segment} entities.
 *
 * <p>For filtered listing queries, prefer {@link #findByFilters} over single-purpose
 * derived query methods — it handles all optional filter combinations in one query.</p>
 */
public interface SegmentRepository extends JpaRepository<Segment, Long> {

    List<Segment> findByTenantIdAndComponent(Long tenantId, boolean component);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndComponent(Long tenantId, boolean component);

    long countByComponent(boolean component);

    Page<Segment> findByTenantIdAndIdIn(Long tenantId, List<Long> ids, Pageable pageable);

    /**
     * Dynamic query for segment listing with optional filters.
     * All filter parameters are optional — pass null to skip a filter.
     *
     * <p>Note: The {@code LOWER(name) LIKE} pattern bypasses standard B-tree indexes.
     * For high-traffic deployments, consider adding a {@code pg_trgm} GIN index:
     * {@code CREATE INDEX idx_segments_name_trgm ON segments USING gin (LOWER(name) gin_trgm_ops);}</p>
     */
    @Query("SELECT s FROM Segment s WHERE s.tenantId = :tenantId "
            + "AND (CAST(:name AS string) IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) "
            + "AND (CAST(:categoryId AS long) IS NULL OR s.categoryId = :categoryId) "
            + "AND (CAST(:segmentType AS string) IS NULL OR s.segmentType = :segmentType) "
            + "AND (CAST(:isComponent AS boolean) IS NULL OR s.component = :isComponent) "
            + "AND (:segmentIds IS NULL OR s.id IN :segmentIds)")
    Page<Segment> findByFilters(@Param("tenantId") Long tenantId,
                                @Param("name") String name,
                                @Param("categoryId") Long categoryId,
                                @Param("segmentType") String segmentType,
                                @Param("isComponent") Boolean isComponent,
                                @Param("segmentIds") List<Long> segmentIds,
                                Pageable pageable);
}
