package com.docgen.controller;

import com.docgen.dto.ReviewActionRequest;
import com.docgen.dto.SegmentReviewDTO;
import com.docgen.dto.UserPrincipal;
import com.docgen.service.SegmentReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for segment-level review actions (approve/reject).
 */
@RestController
@RequestMapping("/api/segment-reviews")
public class SegmentReviewController {

    private final SegmentReviewService segmentReviewService;

    public SegmentReviewController(SegmentReviewService segmentReviewService) {
        this.segmentReviewService = segmentReviewService;
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<SegmentReviewDTO> approveSegmentReview(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comment = request != null ? request.getComment() : null;
        return ResponseEntity.ok(
                segmentReviewService.approveSegmentReview(id, comment, principal.getUserId()));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<SegmentReviewDTO> rejectSegmentReview(
            @PathVariable Long id,
            @Valid @RequestBody ReviewActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                segmentReviewService.rejectSegmentReview(id, request.getComment(), principal.getUserId()));
    }
}
