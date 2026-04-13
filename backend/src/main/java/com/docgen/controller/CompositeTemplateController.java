package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for Composite_Template management.
 * Provides endpoints for creating composite templates, managing assembly configs,
 * previewing (full and selective), and listing segments.
 */
@RestController
@RequestMapping("/api/composite-templates")
public class CompositeTemplateController {

    private static final Logger log = LoggerFactory.getLogger(CompositeTemplateController.class);

    private final CompositeTemplateService compositeTemplateService;
    private final DependencyGraphService dependencyGraphService;
    private final CompositeCoverageService compositeCoverageService;
    private final TemplateReviewService templateReviewService;
    private final SegmentReviewService segmentReviewService;
    private final CompositeImportExportService compositeImportExportService;
    private final SegmentTestService segmentTestService;
    private final SegmentRecommendationService segmentRecommendationService;

    public CompositeTemplateController(CompositeTemplateService compositeTemplateService,
                                       DependencyGraphService dependencyGraphService,
                                       CompositeCoverageService compositeCoverageService,
                                       TemplateReviewService templateReviewService,
                                       SegmentReviewService segmentReviewService,
                                       CompositeImportExportService compositeImportExportService,
                                       SegmentTestService segmentTestService,
                                       SegmentRecommendationService segmentRecommendationService) {
        this.compositeTemplateService = compositeTemplateService;
        this.dependencyGraphService = dependencyGraphService;
        this.compositeCoverageService = compositeCoverageService;
        this.templateReviewService = templateReviewService;
        this.segmentReviewService = segmentReviewService;
        this.compositeImportExportService = compositeImportExportService;
        this.segmentTestService = segmentTestService;
        this.segmentRecommendationService = segmentRecommendationService;
    }

    // ── Create ──

    @PostMapping
    public ResponseEntity<TemplateDTO> createCompositeTemplate(
            @Valid @RequestBody CreateCompositeTemplateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO created = compositeTemplateService.createCompositeTemplate(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── Assembly Config ──

    @GetMapping("/{id}/assembly-config")
    public ResponseEntity<AssemblyConfigDTO> getAssemblyConfig(@PathVariable Long id) {
        return ResponseEntity.ok(compositeTemplateService.getAssemblyConfig(id));
    }

    @PutMapping("/{id}/assembly-config")
    public ResponseEntity<AssemblyConfigDTO> updateAssemblyConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssemblyConfigRequest request) {
        return ResponseEntity.ok(compositeTemplateService.updateAssemblyConfig(id, request));
    }

    // ── Preview ──

    @PostMapping("/{id}/preview")
    public ResponseEntity<CompositePreviewDTO> previewCompositeTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(compositeTemplateService.previewCompositeTemplate(id));
    }

    @PostMapping("/{id}/preview/selective")
    public ResponseEntity<CompositePreviewDTO> previewSelective(
            @PathVariable Long id,
            @Valid @RequestBody SelectivePreviewRequest request) {
        return ResponseEntity.ok(
                compositeTemplateService.previewSelectiveSegments(id, request.getSegmentIds()));
    }

    // ── Coverage ──

    @GetMapping("/{id}/coverage")
    public ResponseEntity<CompositeCoverageReport> getCoverage(@PathVariable Long id) {
        return ResponseEntity.ok(compositeCoverageService.checkCoverage(id));
    }

    // ── Segments ──

    @GetMapping("/{id}/segments")
    public ResponseEntity<List<SegmentDTO>> getSegments(@PathVariable Long id) {
        return ResponseEntity.ok(dependencyGraphService.getSegmentsForTemplate(id));
    }

    // ── Reviews ──

    /**
     * Submit a composite template for review with per-segment reviewer assignments.
     * Creates a parent TemplateReview and individual SegmentReview records.
     */
    @PostMapping("/{id}/reviews")
    public ResponseEntity<List<SegmentReviewDTO>> submitForReview(
            @PathVariable Long id,
            @Valid @RequestBody SubmitCompositeReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Create the parent template review using existing TemplateReviewService
        SubmitReviewRequest templateReviewRequest = new SubmitReviewRequest();
        // Collect unique reviewer IDs from assignments
        List<Long> reviewerIds = request.getAssignments().stream()
                .map(SegmentReviewerAssignment::getReviewerId)
                .distinct()
                .toList();
        templateReviewRequest.setReviewerIds(reviewerIds);
        templateReviewRequest.setReviewLevel(request.getReviewLevel());
        List<TemplateReviewDTO> templateReviews = templateReviewService.submitForReview(id, templateReviewRequest);

        // Use the first template review as the parent for segment reviews
        Long templateReviewId = templateReviews.get(0).getId();
        List<SegmentReviewDTO> segmentReviews = segmentReviewService.createSegmentReviews(
                templateReviewId, request.getAssignments());

        return ResponseEntity.status(HttpStatus.CREATED).body(segmentReviews);
    }

    /**
     * Get segment-level review status for a specific template review.
     */
    @GetMapping("/{id}/reviews/{reviewId}/segments")
    public ResponseEntity<List<SegmentReviewDTO>> getSegmentReviews(
            @PathVariable Long id,
            @PathVariable Long reviewId) {
        return ResponseEntity.ok(segmentReviewService.getSegmentReviews(reviewId));
    }

    // ── Import / Export ──

    /**
     * Export a composite template as a ZIP containing all Segment .docx files + config.json.
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportAsZip(@PathVariable Long id) {
        byte[] zipBytes = compositeImportExportService.exportAsZip(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"composite-template.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    /**
     * Export only the JSON configuration of a composite template.
     */
    @GetMapping("/{id}/export-config")
    public ResponseEntity<byte[]> exportConfig(@PathVariable Long id) {
        byte[] configBytes = compositeImportExportService.exportConfig(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"composite-config.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(configBytes);
    }

    /**
     * Import a composite template from a ZIP file.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDTO> importFromZip(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO result = compositeImportExportService.importFromZip(file, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ── Recommendations ──

    /**
     * Get segment recommendations for a composite template based on current segment types.
     */
    @GetMapping("/{id}/recommendations")
    public ResponseEntity<List<SegmentRecommendationDTO>> getRecommendations(@PathVariable Long id) {
        return ResponseEntity.ok(segmentRecommendationService.recommendSegments(id));
    }

    // ── Composite Tests ──

    /**
     * Run all segment-level + composite-level tests for a composite template.
     */
    @PostMapping("/{id}/tests/run")
    public ResponseEntity<CompositeTestReportDTO> runAllTests(@PathVariable Long id) {
        return ResponseEntity.ok(segmentTestService.runAllCompositeTests(id));
    }
}
