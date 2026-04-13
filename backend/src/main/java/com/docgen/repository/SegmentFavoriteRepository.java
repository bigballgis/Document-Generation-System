package com.docgen.repository;

import com.docgen.entity.SegmentFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SegmentFavorite} entities.
 */
public interface SegmentFavoriteRepository extends JpaRepository<SegmentFavorite, Long> {

    Optional<SegmentFavorite> findByUserIdAndSegmentId(Long userId, Long segmentId);

    List<SegmentFavorite> findByUserId(Long userId);

    boolean existsBySegmentIdAndUserId(Long segmentId, Long userId);

    void deleteBySegmentIdAndUserId(Long segmentId, Long userId);
}
