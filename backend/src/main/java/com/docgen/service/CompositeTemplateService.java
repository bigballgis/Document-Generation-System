package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.CompositePreviewDTO;
import com.docgen.dto.CreateCompositeTemplateRequest;
import com.docgen.dto.TemplateDTO;
import com.docgen.dto.UpdateAssemblyConfigRequest;
import com.docgen.entity.Segment;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing Composite_Templates.
 * Handles creation, assembly config updates, activation, and preview of composite templates.
 */
@Service
public class CompositeTemplateService {

    private static final Logger log = LoggerFactory.getLogger(CompositeTemplateService.class);

    private final TemplateRepository templateRepository;
    private final SegmentRepository segmentRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final DependencyGraphService dependencyGraphService;

    public CompositeTemplateService(TemplateRepository templateRepository,
                                    SegmentRepository segmentRepository,
                                    AssemblyConfigService assemblyConfigService,
                                    DependencyGraphService dependencyGraphService) {
        this.templateRepository = templateRepository;
        this.segmentRepository = segmentRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.dependencyGraphService = dependencyGraphService;
    }

    // ── Create ──

    /**
     * Create a new Composite_Template with template_type = COMPOSITE.
     * Reuses the Template entity and its state machine (initial status: DRAFT).
     *
     * @param request the creation request containing name, description, etc.
     * @param userId  the ID of the user creating the template
     * @return the created template as a DTO
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
     *
     * @param templateId the ID of the composite template
     * @param request    the update request containing the new segment list
     * @return the updated assembly config DTO
     */
    @Transactional
    public AssemblyConfigDTO updateAssemblyConfig(Long templateId, UpdateAssemblyConfigRequest request) {
        Template template = findCompositeTemplateOrThrow(templateId);

        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(request.getSegments());

        // Validate: at least one enabled segment, all segment IDs exist
        assemblyConfigService.validate(config);

        String json = assemblyConfigService.serialize(config);
        template.setAssemblyConfig(json);
        templateRepository.save(template);

        log.info("Assembly config updated for composite template id={}", templateId);
        return config;
    }

    // ── Activate ──

    /**
     * Activate a Composite_Template after verifying all referenced segments exist and are accessible.
     *
     * @param templateId the ID of the composite template to activate
     * @return the activated template as a DTO
     */
    @Transactional
    public TemplateDTO activateCompositeTemplate(Long templateId) {
        Template template = findCompositeTemplateOrThrow(templateId);

        // Parse and validate assembly config
        String assemblyConfigJson = template.getAssemblyConfig();
        if (assemblyConfigJson == null || assemblyConfigJson.isBlank()) {
            throw new BusinessException(ErrorCode.COMPOSITE_TEMPLATE_EMPTY,
                    "Cannot activate composite template without assembly config",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        AssemblyConfigDTO config = assemblyConfigService.deserialize(assemblyConfigJson);

        // Verify all referenced segments exist and are accessible
        List<AssemblySegmentEntry> segments = config.getSegments();
        if (segments == null || segments.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPOSITE_TEMPLATE_EMPTY,
                    "Cannot activate composite template with no segments",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Set<Long> referencedIds = segments.stream()
                .map(AssemblySegmentEntry::getSegmentId)
                .collect(Collectors.toSet());

        Map<Long, Segment> existingSegments = segmentRepository.findAllById(referencedIds).stream()
                .collect(Collectors.toMap(Segment::getId, s -> s));

        Set<Long> missingIds = referencedIds.stream()
                .filter(id -> !existingSegments.containsKey(id))
                .collect(Collectors.toSet());

        if (!missingIds.isEmpty()) {
            log.warn("Activation blocked: composite template id={} references missing segments: {}", templateId, missingIds);
            throw new BusinessException(ErrorCode.SEGMENT_NOT_FOUND,
                    "Cannot activate: segments not found: " + missingIds,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Verify tenant isolation — all segments must belong to the same tenant
        Long tenantId = template.getTenantId();
        List<Long> crossTenantIds = existingSegments.values().stream()
                .filter(s -> !tenantId.equals(s.getTenantId()))
                .map(Segment::getId)
                .toList();

        if (!crossTenantIds.isEmpty()) {
            log.warn("Activation blocked: composite template id={} references cross-tenant segments: {}", templateId, crossTenantIds);
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED,
                    "Cannot activate: segments not accessible (cross-tenant): " + crossTenantIds,
                    HttpStatus.FORBIDDEN);
        }

        template.setStatus("ACTIVE");
        Template saved = templateRepository.save(template);

        log.info("Composite template activated: id={}", templateId);
        return toTemplateDTO(saved);
    }

    // ── Get Assembly Config ──

    /**
     * Retrieve the assembly configuration of a Composite_Template.
     *
     * @param templateId the ID of the composite template
     * @return the assembly config DTO, or an empty config if none is set
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
     * This is a structural preview — it does not perform actual rendering,
     * but validates segment availability and builds the preview metadata.
     *
     * @param templateId the ID of the composite template
     * @return a preview DTO with per-segment status
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

        // Batch-load all referenced segments
        Set<Long> segmentIds = config.getSegments().stream()
                .map(AssemblySegmentEntry::getSegmentId)
                .collect(Collectors.toSet());
        Map<Long, Segment> segmentMap = segmentRepository.findAllById(segmentIds).stream()
                .collect(Collectors.toMap(Segment::getId, s -> s));

        for (AssemblySegmentEntry entry : config.getSegments()) {
            CompositePreviewDTO.SegmentPreviewEntry previewEntry = new CompositePreviewDTO.SegmentPreviewEntry();
            previewEntry.setSegmentId(entry.getSegmentId());

            Segment segment = segmentMap.get(entry.getSegmentId());
            if (segment == null) {
                previewEntry.setSegmentName("Unknown");
                previewEntry.setStatus("NOT_FOUND");
                previewEntry.setErrorMessage("Segment not found: " + entry.getSegmentId());
            } else if (!entry.isEnabled()) {
                previewEntry.setSegmentName(segment.getName());
                previewEntry.setStatus("DISABLED");
            } else {
                previewEntry.setSegmentName(segment.getName());
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
     * Generate a selective preview of the composite template for a subset of segments.
     *
     * @param templateId the ID of the composite template
     * @param segmentIds the subset of segment IDs to preview
     * @return a preview DTO with per-segment status for the selected segments only
     */
    @Transactional(readOnly = true)
    public CompositePreviewDTO previewSelectiveSegments(Long templateId, List<Long> segmentIds) {
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

        Set<Long> selectedIds = new java.util.HashSet<>(segmentIds);

        // Batch-load selected segments
        Map<Long, Segment> segmentMap = segmentRepository.findAllById(selectedIds).stream()
                .collect(Collectors.toMap(Segment::getId, s -> s));

        // Filter assembly config entries to only include selected segments
        for (AssemblySegmentEntry entry : config.getSegments()) {
            if (!selectedIds.contains(entry.getSegmentId())) {
                continue;
            }

            CompositePreviewDTO.SegmentPreviewEntry previewEntry = new CompositePreviewDTO.SegmentPreviewEntry();
            previewEntry.setSegmentId(entry.getSegmentId());

            Segment segment = segmentMap.get(entry.getSegmentId());
            if (segment == null) {
                previewEntry.setSegmentName("Unknown");
                previewEntry.setStatus("NOT_FOUND");
                previewEntry.setErrorMessage("Segment not found: " + entry.getSegmentId());
            } else if (!entry.isEnabled()) {
                previewEntry.setSegmentName(segment.getName());
                previewEntry.setStatus("DISABLED");
            } else {
                previewEntry.setSegmentName(segment.getName());
                previewEntry.setStatus("READY");
            }

            segmentPreviews.add(previewEntry);
        }

        preview.setSegmentPreviews(segmentPreviews);
        log.debug("Selective preview generated for composite template id={}: {} of {} segments",
                templateId, segmentPreviews.size(), segmentIds.size());
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
