package com.docgen.service;

import com.docgen.dto.SegmentReviewDTO;
import com.docgen.dto.SegmentReviewerAssignment;
import com.docgen.entity.ReviewStatus;
import com.docgen.entity.SegmentReview;
import com.docgen.entity.TemplateReview;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentReviewRepository;
import com.docgen.repository.TemplateReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service handling segment-level review workflow for composite templates.
 * Each segment within a composite template can have an independent reviewer.
 */
@Service
public class SegmentReviewService {

    private static final Logger log = LoggerFactory.getLogger(SegmentReviewService.class);

    private final SegmentReviewRepository segmentReviewRepository;
    private final TemplateReviewRepository templateReviewRepository;

    public SegmentReviewService(SegmentReviewRepository segmentReviewRepository,
                                TemplateReviewRepository templateReviewRepository) {
        this.segmentReviewRepository = segmentReviewRepository;
        this.templateReviewRepository = templateReviewRepository;
    }

    /**
     * Create segment-level reviews for a composite template review.
     * Each assignment maps a segment to an independent reviewer.
     */
    @Transactional
    public List<SegmentReviewDTO> createSegmentReviews(Long templateReviewId,
                                                        List<SegmentReviewerAssignment> assignments) {
        // Verify the parent template review exists
        templateReviewRepository.findById(templateReviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.REVIEW_NOT_FOUND, "模板审查记录不存在"));

        List<SegmentReview> reviews = assignments.stream().map(assignment -> {
            SegmentReview review = new SegmentReview();
            review.setTemplateReviewId(templateReviewId);
            review.setSegmentId(assignment.getSegmentId());
            review.setReviewerId(assignment.getReviewerId());
            review.setStatus("PENDING");
            return segmentReviewRepository.save(review);
        }).collect(Collectors.toList());

        log.info("Segment reviews created: templateReviewId={}, count={}",
                templateReviewId, reviews.size());
        return reviews.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Approve a segment review.
     */
    @Transactional
    public SegmentReviewDTO approveSegmentReview(Long segmentReviewId, String comment, Long reviewerId) {
        SegmentReview review = findReviewOrThrow(segmentReviewId);
        validateReviewerAndPending(review, reviewerId);

        review.setStatus("APPROVED");
        review.setComment(comment);
        review.setCompletedAt(Instant.now());
        SegmentReview saved = segmentReviewRepository.save(review);

        log.info("Segment review approved: id={}, segmentId={}", segmentReviewId, review.getSegmentId());
        checkAndUpdateCompositeReviewStatus(review.getTemplateReviewId());

        return toDTO(saved);
    }

    /**
     * Reject a segment review.
     */
    @Transactional
    public SegmentReviewDTO rejectSegmentReview(Long segmentReviewId, String reason, Long reviewerId) {
        SegmentReview review = findReviewOrThrow(segmentReviewId);
        validateReviewerAndPending(review, reviewerId);

        review.setStatus("REJECTED");
        review.setComment(reason);
        review.setCompletedAt(Instant.now());
        SegmentReview saved = segmentReviewRepository.save(review);

        log.info("Segment review rejected: id={}, segmentId={}", segmentReviewId, review.getSegmentId());
        checkAndUpdateCompositeReviewStatus(review.getTemplateReviewId());

        return toDTO(saved);
    }

    /**
     * Get all segment reviews for a template review.
     */
    @Transactional(readOnly = true)
    public List<SegmentReviewDTO> getSegmentReviews(Long templateReviewId) {
        return segmentReviewRepository.findByTemplateReviewId(templateReviewId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Check all segment reviews for a template review and update the composite review status.
     * All approved → composite approved; any rejected → composite rejected.
     */
    void checkAndUpdateCompositeReviewStatus(Long templateReviewId) {
        List<SegmentReview> segmentReviews = segmentReviewRepository.findByTemplateReviewId(templateReviewId);

        if (segmentReviews.isEmpty()) {
            return;
        }

        boolean anyRejected = segmentReviews.stream()
                .anyMatch(r -> "REJECTED".equals(r.getStatus()));
        boolean allApproved = segmentReviews.stream()
                .allMatch(r -> "APPROVED".equals(r.getStatus()));

        if (anyRejected) {
            updateTemplateReviewStatus(templateReviewId, ReviewStatus.REJECTED);
            log.info("Composite review rejected due to segment rejection: templateReviewId={}", templateReviewId);
        } else if (allApproved) {
            updateTemplateReviewStatus(templateReviewId, ReviewStatus.APPROVED);
            log.info("Composite review approved (all segments approved): templateReviewId={}", templateReviewId);
        }
    }

    // ── Private helpers ──

    private void updateTemplateReviewStatus(Long templateReviewId, ReviewStatus status) {
        TemplateReview templateReview = templateReviewRepository.findById(templateReviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.REVIEW_NOT_FOUND, "模板审查记录不存在"));

        // Only update if still PENDING
        if (templateReview.getStatus() == ReviewStatus.PENDING) {
            templateReview.setStatus(status);
            templateReview.setCompletedAt(Instant.now());
            templateReviewRepository.save(templateReview);
        }
    }

    private SegmentReview findReviewOrThrow(Long segmentReviewId) {
        return segmentReviewRepository.findById(segmentReviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.REVIEW_NOT_FOUND, "段落审查记录不存在"));
    }

    private void validateReviewerAndPending(SegmentReview review, Long reviewerId) {
        if (!"PENDING".equals(review.getStatus())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_COMPLETED,
                    "该段落审查已完成", HttpStatus.CONFLICT);
        }
        if (!review.getReviewerId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AUTHORIZED,
                    "非指定审查人，无权操作", HttpStatus.FORBIDDEN);
        }
    }

    private SegmentReviewDTO toDTO(SegmentReview review) {
        SegmentReviewDTO dto = new SegmentReviewDTO();
        dto.setId(review.getId());
        dto.setTemplateReviewId(review.getTemplateReviewId());
        dto.setSegmentId(review.getSegmentId());
        dto.setReviewerId(review.getReviewerId());
        dto.setStatus(review.getStatus());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setCompletedAt(review.getCompletedAt());
        return dto;
    }
}
