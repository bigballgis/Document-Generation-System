package com.docgen.repository;

import com.docgen.entity.SegmentReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SegmentReview} entities.
 */
public interface SegmentReviewRepository extends JpaRepository<SegmentReview, Long> {

    List<SegmentReview> findByTemplateReviewId(Long templateReviewId);

    List<SegmentReview> findBySegmentId(Long segmentId);

    List<SegmentReview> findByReviewerId(Long reviewerId);

    List<SegmentReview> findByTemplateReviewIdAndStatus(Long templateReviewId, String status);
}
