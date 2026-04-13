package com.docgen.repository;

import com.docgen.entity.ReviewStatus;
import com.docgen.entity.TemplateReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TemplateReview} entities.
 */
public interface TemplateReviewRepository extends JpaRepository<TemplateReview, Long> {

    List<TemplateReview> findByTemplateIdOrderByReviewLevelAscCreatedAtDesc(Long templateId);

    Page<TemplateReview> findByTemplateId(Long templateId, Pageable pageable);

    Page<TemplateReview> findByReviewerId(Long reviewerId, Pageable pageable);

    Page<TemplateReview> findByStatus(ReviewStatus status, Pageable pageable);

    Page<TemplateReview> findByReviewerIdAndStatus(Long reviewerId, ReviewStatus status, Pageable pageable);

    List<TemplateReview> findByTemplateIdAndReviewLevel(Long templateId, int reviewLevel);

    boolean existsByTemplateIdAndReviewLevelAndStatus(Long templateId, int reviewLevel, ReviewStatus status);
}
