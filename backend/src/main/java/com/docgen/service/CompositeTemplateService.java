package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.CompositePreviewDTO;
import com.docgen.dto.CreateCompositeTemplateRequest;
import com.docgen.dto.TemplateDTO;
import com.docgen.dto.UpdateAssemblyConfigRequest;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for managing Composite_Templates.
 * Handles creation, assembly config updates, activation, and preview of composite templates.
 */
@Service
public class CompositeTemplateService {

    private static final Logger log = LoggerFactory.getLogger(CompositeTemplateService.class);

    private final TemplateRepository templateRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final TemplateStateMachineService templateStateMachineService;

    public CompositeTemplateService(TemplateRepository templateRepository,
                                    AssemblyConfigService assemblyConfigService,
                                    TemplateStateMachineService templateStateMachineService) {
        this.templateRepository = templateRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.templateStateMachineService = templateStateMachineService;
    }

    // ── Create ──

    /**
     * Create a new Composite_Template with template_type = COMPOSITE.
     * Reuses the Template entity and its state machine (initial status: DRAFT).
     */
    @Transactional
    public TemplateDTO createCompositeTemplate(CreateCompositeTemplateRequest request, Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setTemplateType("COMPOSITE");
        template.setStatus("DRAFT");
        template.setCreatedBy(userId);
        template.setTemplateFilePath("");
        template.setOutputFormat(request.getOutputFormat() != null ? request.getOutputFormat() : "WORD");
        template.setTeamId(request.getTeamId());
        template.setCategoryId(request.getCategoryId());

        Template saved = templateRepository.save(template);

        log.info("Composite template created: name={}, id={}, tenantId={}", saved.getName(), saved.getId(), tenantId);
        return toTemplateDTO(saved);
    }

    // ── Update Assembly Config ──

    /**
     * Update the assembly configuration of a Composite_Template.
     * Delegates validation to {@link AssemblyConfigService} before persisting.
     */
    @Transactional
    public AssemblyConfigDTO updateAssemblyConfig(Long templateId, UpdateAssemblyConfigRequest request) {
        Template template = findCompositeTemplateOrThrow(templateId);

        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(request.getSegments());

        // Validate: at least one enabled segment, all filePaths non-empty
        assemblyConfigService.validate(config);

        String json = assemblyConfigService.serialize(config);
        template.setAssemblyConfig(json);
        templateRepository.save(template);

        log.info("Assembly config updated for composite template id={}", templateId);
        return config;
    }

    // ── Activate ──

    /**
     * Activate a Composite_Template after verifying all inline segments have valid filePaths.
     * <p>
     * State changes go through {@link TemplateStateMachineService} (same rules as single templates):
     * {@code DRAFT → ACTIVE} only when {@code reviewRequired} is false; {@code REVIEWED → ACTIVE} is allowed;
     * illegal transitions yield {@link ErrorCode#TEMPLATE_INVALID_STATE_TRANSITION} or
     * {@link ErrorCode#TEMPLATE_REVIEW_REQUIRED}. If the template is already {@code ACTIVE}, this method returns
     * after validation without calling the state machine again.
     */
    @Transactional
    public TemplateDTO activateCompositeTemplate(Long templateId) {
        Template template = findCompositeTemplateOrThrow(templateId);

        String assemblyConfigJson = template.getAssemblyConfig();
        if (assemblyConfigJson == null || assemblyConfigJson.isBlank()) {
            throw new BusinessException(ErrorCode.COMPOSITE_TEMPLATE_EMPTY,
                    "Cannot activate composite template without assembly config",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        AssemblyConfigDTO config = assemblyConfigService.deserialize(assemblyConfigJson);

        List<AssemblySegmentEntry> segments = config.getSegments();
        if (segments == null || segments.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPOSITE_TEMPLATE_EMPTY,
                    "Cannot activate composite template with no segments",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Verify all enabled segments have non-empty filePath
        for (AssemblySegmentEntry entry : segments) {
            if (entry.isEnabled() && (entry.getFilePath() == null || entry.getFilePath().isBlank())) {
                throw new BusinessException(ErrorCode.ASSEMBLY_CONFIG_INVALID,
                        "Cannot activate: segment '" + entry.getName() + "' has no file path",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        if (TemplateState.ACTIVE.name().equals(template.getStatus())) {
            log.info("Composite template already ACTIVE: id={}", templateId);
            return toTemplateDTO(template);
        }

        Template saved = templateStateMachineService.transition(templateId, TemplateState.ACTIVE);

        log.info("Composite template activated: id={}", templateId);
        return toTemplateDTO(saved);
    }

    // ── Get Assembly Config ──

    /**
     * Retrieve the assembly configuration of a Composite_Template.
     */
    @Transactional(readOnly = true)
    public AssemblyConfigDTO getAssemblyConfig(Long templateId) {
        Template template = findCompositeTemplateOrThrow(templateId);

        String json = template.getAssemblyConfig();
        if (json == null || json.isBlank()) {
            AssemblyConfigDTO empty = new AssemblyConfigDTO();
            empty.setSegments(Collections.emptyList());
            return empty;
        }

        return assemblyConfigService.deserialize(json);
    }

    // ── Preview ──

    /**
     * Generate a preview of the composite template showing each segment's status.
     * Reads segment names and status from assembly_config inline data.
     */
    @Transactional(readOnly = true)
    public CompositePreviewDTO previewCompositeTemplate(Long templateId) {
        Template template = findCompositeTemplateOrThrow(templateId);

        CompositePreviewDTO preview = new CompositePreviewDTO();
        List<CompositePreviewDTO.SegmentPreviewEntry> segmentPreviews = new ArrayList<>();

        String json = template.getAssemblyConfig();
        if (json == null || json.isBlank()) {
            preview.setSegmentPreviews(Collections.emptyList());
            return preview;
        }

        AssemblyConfigDTO config = assemblyConfigService.deserialize(json);
        if (config.getSegments() == null) {
            preview.setSegmentPreviews(Collections.emptyList());
            return preview;
        }

        for (AssemblySegmentEntry entry : config.getSegments()) {
            CompositePreviewDTO.SegmentPreviewEntry previewEntry = new CompositePreviewDTO.SegmentPreviewEntry();
            previewEntry.setSegmentName(entry.getName() != null ? entry.getName() : "Unknown");

            if (entry.getFilePath() == null || entry.getFilePath().isBlank()) {
                previewEntry.setStatus("NOT_FOUND");
                previewEntry.setErrorMessage("Segment file path is missing");
            } else if (!entry.isEnabled()) {
                previewEntry.setStatus("DISABLED");
            } else {
                previewEntry.setStatus("READY");
            }

            segmentPreviews.add(previewEntry);
        }

        preview.setSegmentPreviews(segmentPreviews);
        log.debug("Preview generated for composite template id={}: {} segments", templateId, segmentPreviews.size());
        return preview;
    }

    // ── Selective Preview ──

    /**
     * Generate a selective preview of the composite template for a subset of segment positions.
     */
    @Transactional(readOnly = true)
    public CompositePreviewDTO previewSelectiveSegments(Long templateId, List<Integer> positions) {
        Template template = findCompositeTemplateOrThrow(templateId);

        CompositePreviewDTO preview = new CompositePreviewDTO();
        List<CompositePreviewDTO.SegmentPreviewEntry> segmentPreviews = new ArrayList<>();

        String json = template.getAssemblyConfig();
        if (json == null || json.isBlank()) {
            preview.setSegmentPreviews(Collections.emptyList());
            return preview;
        }

        AssemblyConfigDTO config = assemblyConfigService.deserialize(json);
        if (config.getSegments() == null) {
            preview.setSegmentPreviews(Collections.emptyList());
            return preview;
        }

        Set<Integer> selectedPositions = new HashSet<>(positions);

        for (AssemblySegmentEntry entry : config.getSegments()) {
            if (entry.getPosition() == null || !selectedPositions.contains(entry.getPosition())) {
                continue;
            }

            CompositePreviewDTO.SegmentPreviewEntry previewEntry = new CompositePreviewDTO.SegmentPreviewEntry();
            previewEntry.setSegmentName(entry.getName() != null ? entry.getName() : "Unknown");

            if (entry.getFilePath() == null || entry.getFilePath().isBlank()) {
                previewEntry.setStatus("NOT_FOUND");
                previewEntry.setErrorMessage("Segment file path is missing");
            } else if (!entry.isEnabled()) {
                previewEntry.setStatus("DISABLED");
            } else {
                previewEntry.setStatus("READY");
            }

            segmentPreviews.add(previewEntry);
        }

        preview.setSegmentPreviews(segmentPreviews);
        log.debug("Selective preview generated for composite template id={}: {} of {} positions",
                templateId, segmentPreviews.size(), positions.size());
        return preview;
    }

    // ── Private helpers ──

    private Template findCompositeTemplateOrThrow(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在: " + templateId));

        if (!"COMPOSITE".equals(template.getTemplateType())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "Template is not a composite template: " + templateId,
                    HttpStatus.BAD_REQUEST);
        }

        return template;
    }

    private TemplateDTO toTemplateDTO(Template template) {
        return new TemplateDTO(
                template.getId(),
                template.getTenantId(),
                template.getName(),
                template.getDescription(),
                template.getTemplateFilePath(),
                template.getOutputFormat(),
                template.getStorageStrategy(),
                template.isAsync(),
                template.getTeamId(),
                template.getCreatedBy(),
                template.getCategoryId(),
                template.isReviewRequired(),
                template.getStatus(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
