package com.docgen.repository;

import com.docgen.entity.SegmentTagMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SegmentTagMapping} entities.
 */
public interface SegmentTagMappingRepository extends JpaRepository<SegmentTagMapping, Long> {

    List<SegmentTagMapping> findBySegmentId(Long segmentId);

    List<SegmentTagMapping> findByTagId(Long tagId);

    void deleteBySegmentIdAndTagId(Long segmentId, Long tagId);

    boolean existsBySegmentIdAndTagId(Long segmentId, Long tagId);

    /**
     * Find segment IDs that have ALL of the specified tags.
     */
    @Query("SELECT m.segmentId FROM SegmentTagMapping m WHERE m.tagId IN :tagIds "
            + "GROUP BY m.segmentId HAVING COUNT(DISTINCT m.tagId) = :tagCount")
    List<Long> findSegmentIdsHavingAllTags(@Param("tagIds") List<Long> tagIds,
                                           @Param("tagCount") long tagCount);

    void deleteBySegmentId(Long segmentId);
}
