package com.docgen.repository;

import com.docgen.entity.SegmentVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SegmentVersion} entities.
 */
public interface SegmentVersionRepository extends JpaRepository<SegmentVersion, Long> {

    /**
     * Find all versions for a segment, ordered by version number descending (newest first).
     */
    List<SegmentVersion> findBySegmentIdOrderByVersionNumberDesc(Long segmentId);

    /**
     * Find versions for a segment with pagination, ordered by version number descending.
     */
    Page<SegmentVersion> findBySegmentIdOrderByVersionNumberDesc(Long segmentId, Pageable pageable);

    /**
     * Find the maximum version number for a given segment.
     * Returns empty if no versions exist yet.
     */
    @Query("SELECT MAX(v.versionNumber) FROM SegmentVersion v WHERE v.segmentId = :segmentId")
    Optional<Integer> findMaxVersionNumberBySegmentId(@Param("segmentId") Long segmentId);

    /**
     * Find a specific version by segment ID and version ID.
     */
    Optional<SegmentVersion> findByIdAndSegmentId(Long id, Long segmentId);

    /**
     * Find a specific version by segment ID and version number.
     */
    Optional<SegmentVersion> findBySegmentIdAndVersionNumber(Long segmentId, Integer versionNumber);

    void deleteBySegmentId(Long segmentId);
}
