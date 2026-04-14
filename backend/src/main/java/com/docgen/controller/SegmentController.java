package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.entity.SegmentFavorite;
import com.docgen.repository.SegmentFavoriteRepository;
import com.docgen.service.SegmentLockService;
import com.docgen.service.SegmentPermissionService;
import com.docgen.service.SegmentRecommendationService;
import com.docgen.service.SegmentService;
import com.docgen.service.SegmentTestService;
import com.docgen.service.SegmentVariableService;
import com.docgen.service.SegmentVersionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for Segment CRUD, clone, promote/demote, and favorite endpoints.
 */
@RestController
@RequestMapping("/api/segments")
public class SegmentController {

    private static final Logger log = LoggerFactory.getLogger(SegmentController.class);

    private final SegmentService segmentService;
    private final SegmentVersionService segmentVersionService;
    private final SegmentVariableService segmentVariableService;
    private final SegmentFavoriteRepository segmentFavoriteRepository;
    private final SegmentLockService segmentLockService;
    private final SegmentPermissionService segmentPermissionService;
    private final SegmentTestService segmentTestService;
    private final SegmentRecommendationService segmentRecommendationService;

    public SegmentController(SegmentService segmentService,
                             SegmentVersionService segmentVersionService,
                             SegmentVariableService segmentVariableService,
                             SegmentFavoriteRepository segmentFavoriteRepository,
                             SegmentLockService segmentLockService,
                             SegmentPermissionService segmentPermissionService,
                             SegmentTestService segmentTestService,
                             SegmentRecommendationService segmentRecommendationService) {
        this.segmentService = segmentService;
        this.segmentVersionService = segmentVersionService;
        this.segmentVariableService = segmentVariableService;
        this.segmentFavoriteRepository = segmentFavoriteRepository;
        this.segmentLockService = segmentLockService;
        this.segmentPermissionService = segmentPermissionService;
        this.segmentTestService = segmentTestService;
        this.segmentRecommendationService = segmentRecommendationService;
    }

    // ── Create ──

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SegmentDTO> createSegment(
            @Valid @RequestPart("request") CreateSegmentRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        SegmentDTO created = segmentService.createSegment(request, file, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── List ──

    @GetMapping
    public ResponseEntity<Page<SegmentDTO>> listSegments(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String segmentType,
            @RequestParam(required = false) Boolean isComponent,
            Pageable pageable) {
        SegmentQueryRequest query = new SegmentQueryRequest();
        query.setName(name);
        query.setTagIds(tagIds);
        query.setCategoryId(categoryId);
        query.setSegmentType(segmentType);
        query.setIsComponent(isComponent);
        return ResponseEntity.ok(segmentService.listSegments(query, pageable));
    }

    // ── Get by ID ──

    @GetMapping("/{id}")
    public ResponseEntity<SegmentDTO> getSegment(@PathVariable Long id) {
        return ResponseEntity.ok(segmentService.getSegment(id));
    }

    // ── Update ──

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SegmentDTO> updateSegment(
            @PathVariable Long id,
            @Valid @RequestPart("request") UpdateSegmentRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        SegmentDTO updated = segmentService.updateSegment(id, request, file, principal.getUserId());
        return ResponseEntity.ok(updated);
    }

    // ── Delete ──

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSegment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        segmentService.deleteSegment(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ── Clone ──

    @PostMapping("/{id}/clone")
    public ResponseEntity<SegmentDTO> cloneSegment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SegmentDTO cloned = segmentService.cloneSegment(id, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(cloned);
    }

    // ── Promote / Demote ──

    @PostMapping("/{id}/promote")
    public ResponseEntity<SegmentDTO> promoteToComponent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(segmentService.promoteToComponent(id, principal.getUserId()));
    }

    @PostMapping("/{id}/demote")
    public ResponseEntity<SegmentDTO> demoteFromComponent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(segmentService.demoteFromComponent(id, principal.getUserId()));
    }

    // ── Versions ──

    @GetMapping("/{id}/versions")
    public ResponseEntity<Page<SegmentVersionDTO>> listVersions(
            @PathVariable Long id,
            Pageable pageable) {
        return ResponseEntity.ok(segmentVersionService.listVersions(id, pageable));
    }

    @PostMapping("/{id}/rollback/{versionId}")
    public ResponseEntity<SegmentDTO> rollbackToVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(segmentVersionService.rollbackToVersion(id, versionId, principal.getUserId()));
    }

    @GetMapping("/{id}/versions/diff")
    public ResponseEntity<VersionDiffResult> compareVersions(
            @PathVariable Long id,
            @RequestParam int versionA,
            @RequestParam int versionB) {
        return ResponseEntity.ok(segmentVersionService.compareVersions(id, versionA, versionB));
    }

    // ── Variables ──

    @GetMapping("/{id}/variables")
    public ResponseEntity<List<SegmentVariableDTO>> getVariables(@PathVariable Long id) {
        return ResponseEntity.ok(segmentVariableService.scanVariables(id));
    }

    // ── Favorite ──

    @PostMapping("/{id}/favorite")
    @Transactional
    public ResponseEntity<Void> addFavorite(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Ensure segment exists
        segmentService.getSegment(id);

        Long userId = principal.getUserId();
        if (!segmentFavoriteRepository.existsBySegmentIdAndUserId(id, userId)) {
            SegmentFavorite favorite = new SegmentFavorite();
            favorite.setSegmentId(id);
            favorite.setUserId(userId);
            segmentFavoriteRepository.save(favorite);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/favorite")
    @Transactional
    public ResponseEntity<Void> removeFavorite(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        segmentFavoriteRepository.deleteBySegmentIdAndUserId(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ── Edit Lock ──

    @PostMapping("/{id}/lock")
    public ResponseEntity<LockInfo> acquireLock(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        LockInfo lockInfo = segmentLockService.acquireLock(id, principal.getUserId(), principal.getUsername());
        if (lockInfo == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.ok(lockInfo);
    }

    @DeleteMapping("/{id}/lock")
    public ResponseEntity<Void> releaseLock(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean released = segmentLockService.releaseLock(id, principal.getUserId());
        if (!released) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/lock/renew")
    public ResponseEntity<LockInfo> renewLock(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        LockInfo lockInfo = segmentLockService.renewLock(id, principal.getUserId());
        if (lockInfo == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.ok(lockInfo);
    }

    @GetMapping("/{id}/lock")
    public ResponseEntity<LockInfo> getLockInfo(@PathVariable Long id) {
        LockInfo lockInfo = segmentLockService.getLockInfo(id);
        if (lockInfo == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(lockInfo);
    }

    // ── Permissions ──

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<PermissionDTO>> getPermissions(@PathVariable Long id) {
        return ResponseEntity.ok(segmentPermissionService.getSegmentPermissions(id));
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<PermissionDTO> grantPermission(
            @PathVariable Long id,
            @Valid @RequestBody GrantPermissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PermissionDTO result = segmentPermissionService.grantPermission(
                id, request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable Long id,
            @PathVariable Long permissionId) {
        segmentPermissionService.revokePermission(id, permissionId);
        return ResponseEntity.noContent().build();
    }

    // ── Test Data ──

    /**
     * Save test data for a segment.
     */
    @PostMapping("/{id}/test-data")
    public ResponseEntity<SegmentTestDataDTO> saveTestData(
            @PathVariable Long id,
            @Valid @RequestBody CreateSegmentTestDataRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SegmentTestDataDTO result = segmentTestService.saveTestData(id, request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * List all test data for a segment.
     */
    @GetMapping("/{id}/test-data")
    public ResponseEntity<List<SegmentTestDataDTO>> listTestData(@PathVariable Long id) {
        return ResponseEntity.ok(segmentTestService.listTestData(id));
    }

    /**
     * Delete a test data entry.
     */
    @DeleteMapping("/{id}/test-data/{testDataId}")
    public ResponseEntity<Void> deleteTestData(
            @PathVariable Long id,
            @PathVariable Long testDataId) {
        segmentTestService.deleteTestData(testDataId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Run a segment test with specific test data.
     */
    @PostMapping("/{id}/test-data/{testDataId}/run")
    public ResponseEntity<SegmentTestResultDTO> runSegmentTest(
            @PathVariable Long id,
            @PathVariable Long testDataId) {
        return ResponseEntity.ok(segmentTestService.runSegmentTest(id, testDataId));
    }

    // ── Duplicate Analysis ──

    /**
     * Analyze duplicate segments within the current tenant.
     */
    @GetMapping("/duplicate-analysis")
    public ResponseEntity<DuplicateAnalysisDTO> analyzeDuplicates() {
        return ResponseEntity.ok(segmentRecommendationService.analyzeDuplicates());
    }

    // ── Segment Templates ──

    /**
     * List preset and custom segment templates.
     */
    @GetMapping("/templates")
    public ResponseEntity<List<SegmentTemplateDTO>> listSegmentTemplates() {
        return ResponseEntity.ok(segmentRecommendationService.listSegmentTemplates());
    }

    /**
     * Save a segment as a custom template.
     */
    @PostMapping("/templates")
    public ResponseEntity<SegmentTemplateDTO> saveAsTemplate(
            @Valid @RequestBody CreateSegmentTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(segmentRecommendationService.saveAsTemplate(request.getSegmentId()));
    }
}
