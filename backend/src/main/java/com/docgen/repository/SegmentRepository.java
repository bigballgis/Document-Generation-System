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
 */
public interface SegmentRepository extends JpaRepository<Segment, Long> {

    Page<Segment> findByTenantId(Long tenantId, Pageable pageable);

    Page<Segment> findByTenantIdAndIsComponent(Long tenantId, boolean isComponent, Pageable pageable);

    Page<Segment> findByTenantIdAndNameContainingIgnoreCase(Long tenantId, String name, Pageable pageable);

    Page<Segment> findByTenantIdAndCategoryId(Long tenantId, Long categoryId, Pageable pageable);

    List<Segment> findByTenantIdAndIsComponent(Long tenantId, boolean isComponent);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndIsComponent(Long tenantId, boolean isComponent);

    long countByIsComponent(boolean isComponent);

    Page<Segment> findByIdIn(List<Long> ids, Pageable pageable);

    /**
     * Dynamic query for segment listing with optional filters.
     * All filter parameters are optional — pass null to skip a filter.
     */
    @Query("SELECT s FROM Segment s WHERE s.tenantId = :tenantId "
            + "AND (:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))) "
            + "AND (:categoryId IS NULL OR s.categoryId = :categoryId) "
            + "AND (:segmentType IS NULL OR s.segmentType = :segmentType) "
            + "AND (:isComponent IS NULL OR s.component = :isComponent) "
            + "AND (:segmentIds IS NULL OR s.id IN :segmentIds)")
    Page<Segment> findByFilters(@Param("tenantId") Long tenantId,
                                @Param("name") String name,
                                @Param("categoryId") Long categoryId,
                                @Param("segmentType") String segmentType,
                                @Param("isComponent") Boolean isComponent,
                                @Param("segmentIds") List<Long> segmentIds,
                                Pageable pageable);
}
