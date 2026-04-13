package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.SegmentDTO;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.Segment;
import com.docgen.entity.Template;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service managing dependency relationships between Segments and Composite_Templates.
 * Queries the assembly_config JSONB to determine which templates reference a given segment.
 */
@Service
public class DependencyGraphService {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphService.class);

    private final TemplateRepository templateRepository;
    private final SegmentRepository segmentRepository;
    private final ObjectMapper objectMapper;

    public DependencyGraphService(TemplateRepository templateRepository,
                                  SegmentRepository segmentRepository,
                                  ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.segmentRepository = segmentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Find all Composite_Templates whose assembly_config references the given segmentId.
     */
    @Transactional(readOnly = true)
    public List<TemplateDTO> getReferencingTemplates(Long segmentId) {
        List<Template> templates = templateRepository.findCompositeTemplatesReferencingSegment(segmentId);
        return templates.stream().map(this::toTemplateDTO).toList();
    }

    /**
     * Count how many Composite_Templates reference the given segmentId.
     */
    @Transactional(readOnly = true)
    public int getReferenceCount(Long segmentId) {
        List<Template> templates = templateRepository.findCompositeTemplatesReferencingSegment(segmentId);
        return templates.size();
    }

    /**
     * Check whether the given segmentId is referenced by any Composite_Template.
     */
    @Transactional(readOnly = true)
    public boolean isReferenced(Long segmentId) {
        List<Template> templates = templateRepository.findCompositeTemplatesReferencingSegment(segmentId);
        return !templates.isEmpty();
    }

    /**
     * Extract all Segment references from a Composite_Template's assembly_config.
     */
    @Transactional(readOnly = true)
    public List<SegmentDTO> getSegmentsForTemplate(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        String assemblyConfigJson = template.getAssemblyConfig();
        if (assemblyConfigJson == null || assemblyConfigJson.isBlank()) {
            return Collections.emptyList();
        }

        AssemblyConfigDTO config = parseAssemblyConfig(assemblyConfigJson);
        if (config == null || config.getSegments() == null) {
            return Collections.emptyList();
        }

        List<SegmentDTO> result = new ArrayList<>();
        for (AssemblySegmentEntry entry : config.getSegments()) {
            if (entry.getSegmentId() != null) {
                segmentRepository.findById(entry.getSegmentId())
                        .ifPresent(segment -> result.add(toSegmentDTO(segment)));
            }
        }
        return result;
    }

    /**
     * Notify designers of all Composite_Templates that reference the given component segment
     * about a new version. Currently implemented as audit log + info logging.
     */
    @Transactional(readOnly = true)
    public void notifyComponentChange(Long segmentId, int newVersionNumber) {
        List<Template> referencingTemplates = templateRepository
                .findCompositeTemplatesReferencingSegment(segmentId);

        if (referencingTemplates.isEmpty()) {
            return;
        }

        for (Template template : referencingTemplates) {
            log.info("Component change notification: segmentId={} version={} affects compositeTemplate={} (id={}, designer={})",
                    segmentId, newVersionNumber, template.getName(), template.getId(), template.getCreatedBy());
        }

        log.info("Component segmentId={} new version {} notified to {} composite template(s)",
                segmentId, newVersionNumber, referencingTemplates.size());
    }

    // ── Private helpers ──

    private AssemblyConfigDTO parseAssemblyConfig(String json) {
        try {
            return objectMapper.readValue(json, AssemblyConfigDTO.class);
        } catch (Exception e) {
            log.warn("Failed to parse assembly_config JSON: {}", e.getMessage());
            return null;
        }
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

    private SegmentDTO toSegmentDTO(Segment segment) {
        SegmentDTO dto = new SegmentDTO();
        dto.setId(segment.getId());
        dto.setName(segment.getName());
        dto.setDescription(segment.getDescription());
        dto.setFilePath(segment.getFilePath());
        dto.setComponent(segment.isComponent());
        dto.setSegmentType(segment.getSegmentType());
        dto.setCreatedBy(segment.getCreatedBy());
        dto.setCategoryId(segment.getCategoryId());
        dto.setTenantId(segment.getTenantId());
        dto.setCreatedAt(segment.getCreatedAt());
        dto.setUpdatedAt(segment.getUpdatedAt());
        return dto;
    }
}
