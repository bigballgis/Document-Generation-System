package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.entity.TemplateState;
import com.docgen.service.CoverageCheckService;
import com.docgen.service.MigrationService;
import com.docgen.service.TemplatePreviewService;
import com.docgen.service.TemplateService;
import com.docgen.service.TemplateStateMachineService;
import com.docgen.service.VersionDiffService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private static final Logger log = LoggerFactory.getLogger(TemplateController.class);

    private final TemplateService templateService;
    private final TemplateStateMachineService stateMachineService;
    private final TemplatePreviewService templatePreviewService;
    private final CoverageCheckService coverageCheckService;
    private final VersionDiffService versionDiffService;
    private final MigrationService migrationService;

    public TemplateController(TemplateService templateService,
                              TemplateStateMachineService stateMachineService,
                              TemplatePreviewService templatePreviewService,
                              CoverageCheckService coverageCheckService,
                              VersionDiffService versionDiffService,
                              MigrationService migrationService) {
        this.templateService = templateService;
        this.stateMachineService = stateMachineService;
        this.templatePreviewService = templatePreviewService;
        this.coverageCheckService = coverageCheckService;
        this.versionDiffService = versionDiffService;
        this.migrationService = migrationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TemplateDTO> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO created = templateService.createTemplate(request, null, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDTO> createTemplateWithFile(
            @Valid @RequestPart("request") CreateTemplateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO created = templateService.createTemplate(request, file, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<Page<TemplateDTO>> listTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> tagIds,
            Pageable pageable) {
        TemplateQueryRequest query = new TemplateQueryRequest(keyword, categoryId, tagIds);
        return ResponseEntity.ok(templateService.listTemplates(query, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateDTO> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    /**
     * Reviewer candidates: same tenant and same team as the template; template author is excluded.
     */
    @GetMapping("/{id}/reviewers/candidates")
    public ResponseEntity<List<ReviewerCandidateDTO>> listReviewerCandidates(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.listReviewerCandidates(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TemplateDTO> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTemplateRequest request) {
        TemplateDTO updated = templateService.updateTemplate(id, request, null);
        try {
            coverageCheckService.checkCoverage(id);
        } catch (Exception e) {
            log.warn("Coverage check failed after template save for template {}: {}", id, e.getMessage());
        }
        return ResponseEntity.ok(updated);
    }

    @PutMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDTO> updateTemplateWithFile(
            @PathVariable Long id,
            @Valid @RequestPart("request") UpdateTemplateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        TemplateDTO updated = templateService.updateTemplate(id, request, file);
        try {
            coverageCheckService.checkCoverage(id);
        } catch (Exception e) {
            log.warn("Coverage check failed after template save for template {}: {}", id, e.getMessage());
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<TemplateDTO> cloneTemplate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.cloneTemplate(id));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<TemplateVersionDTO>> getTemplateVersions(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplateVersions(id));
    }

    @PostMapping("/{id}/rollback/{versionId}")
    public ResponseEntity<TemplateDTO> rollbackToVersion(
            @PathVariable Long id,
            @PathVariable Long versionId) {
        return ResponseEntity.ok(templateService.rollbackToVersion(id, versionId));
    }

    @GetMapping("/{id}/versions/diff")
    public ResponseEntity<VersionDiffResult> compareVersions(
            @PathVariable Long id,
            @RequestParam int versionA,
            @RequestParam int versionB) {
        return ResponseEntity.ok(versionDiffService.compareVersions(id, versionA, versionB));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<PreviewResult> previewTemplate(
            @PathVariable Long id,
            @RequestBody(required = false) PreviewRequest request) {
        if (request == null) {
            request = new PreviewRequest();
        }
        return ResponseEntity.ok(templatePreviewService.preview(id, request));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<TemplateDTO> activateTemplate(@PathVariable Long id) {
        if (coverageCheckService.isBelowThreshold(id, 100.0)) {
            log.warn("Template {} has coverage below 100% threshold during activation", id);
        }
        var template = stateMachineService.transition(id, TemplateState.ACTIVE);
        return ResponseEntity.ok(templateService.getTemplate(template.getId()));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<TemplateDTO> archiveTemplate(@PathVariable Long id) {
        var template = stateMachineService.transition(id, TemplateState.ARCHIVED);
        return ResponseEntity.ok(templateService.getTemplate(template.getId()));
    }

    /**
     * DRAFT → IN_TEST. Run after design is ready; then use {@code POST /api/templates/{id}/reviews} to request review.
     */
    @PostMapping("/{id}/submit-test")
    public ResponseEntity<TemplateDTO> submitToTest(@PathVariable Long id) {
        var template = stateMachineService.transition(id, TemplateState.IN_TEST);
        return ResponseEntity.ok(templateService.getTemplate(template.getId()));
    }

    /**
     * IN_TEST → DRAFT. Return to design for editing.
     */
    @PostMapping("/{id}/return-design")
    public ResponseEntity<TemplateDTO> returnToDesign(@PathVariable Long id) {
        var template = stateMachineService.transition(id, TemplateState.DRAFT);
        return ResponseEntity.ok(templateService.getTemplate(template.getId()));
    }

    /**
     * @deprecated Use {@code submit-test} then create reviews via {@code TemplateReviewController}.
     * Direct DRAFT → PENDING_REVIEW is no longer allowed; transition will fail unless current state is IN_TEST.
     */
    @PostMapping("/{id}/submit-review")
    @Deprecated
    public ResponseEntity<TemplateDTO> submitForReviewStateOnly(@PathVariable Long id) {
        var template = stateMachineService.transition(id, TemplateState.PENDING_REVIEW);
        return ResponseEntity.ok(templateService.getTemplate(template.getId()));
    }

    @GetMapping("/{id}/available-transitions")
    public ResponseEntity<List<TemplateState>> getAvailableTransitions(@PathVariable Long id) {
        return ResponseEntity.ok(stateMachineService.getAvailableTransitions(id));
    }

    @PostMapping("/{id}/create-draft-version")
    public ResponseEntity<TemplateDTO> createDraftVersion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TemplateDTO result = templateService.createDraftVersion(id, principal.getUserId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/migrate-to-composite")
    public ResponseEntity<MigrationResultDTO> migrateToComposite(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        MigrationResultDTO result = migrationService.migrateToComposite(id, principal.getUserId());
        return ResponseEntity.ok(result);
    }
}
