package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.entity.ReviewStatus;
import com.docgen.service.TemplateReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Submit, approve, conditionally approve, and reject template reviews.
 */
@RestController
public class TemplateReviewController {

    private final TemplateReviewService reviewService;

    public TemplateReviewController(TemplateReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * List all reviews globally (admin view), optionally filtered by status.
     */
    @GetMapping("/api/reviews")
    public ResponseEntity<Page<TemplateReviewDTO>> listAllReviews(
            @RequestParam(required = false) ReviewStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(reviewService.listAllReviews(status, pageable));
    }

    /**
     * Submit a template for review.
     */
    @PostMapping("/api/templates/{templateId}/reviews")
    public ResponseEntity<?> submitForReview(
            @PathVariable Long templateId,
            @Valid @RequestBody SubmitReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        var reviews = reviewService.submitForReview(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reviews);
    }

    /**
     * List reviews for a template.
     */
    @GetMapping("/api/templates/{templateId}/reviews")
    public ResponseEntity<Page<TemplateReviewDTO>> listReviews(
            @PathVariable Long templateId,
            Pageable pageable) {
        return ResponseEntity.ok(reviewService.listReviewsByTemplate(templateId, pageable));
    }

    /**
     * Approve a review.
     */
    @PutMapping("/api/reviews/{reviewId}/approve")
    public ResponseEntity<TemplateReviewDTO> approveReview(
            @PathVariable Long reviewId,
            @RequestBody(required = false) ReviewActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comment = request != null ? request.getComment() : null;
        return ResponseEntity.ok(reviewService.approveReview(reviewId, principal.getUserId(), comment));
    }

    /**
     * Conditionally approve a review with suggestions.
     */
    @PutMapping("/api/reviews/{reviewId}/conditional-approve")
    public ResponseEntity<TemplateReviewDTO> conditionalApprove(
            @PathVariable Long reviewId,
            @Valid @RequestBody ConditionalApproveRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reviewService.conditionalApprove(
                reviewId, principal.getUserId(), request));
    }

    /**
     * Reject a review. Reason is required.
     */
    @PutMapping("/api/reviews/{reviewId}/reject")
    public ResponseEntity<TemplateReviewDTO> rejectReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reviewService.rejectReview(
                reviewId, principal.getUserId(), request.getComment()));
    }

    /**
     * Get OnlyOffice review URL for a template (for annotation/comment support).
     */
    @GetMapping("/api/templates/{templateId}/reviews/editor-url")
    public ResponseEntity<String> getReviewEditorUrl(@PathVariable Long templateId) {
        return ResponseEntity.ok(reviewService.getOnlyOfficeReviewUrl(templateId));
    }
}
