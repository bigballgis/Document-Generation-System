package com.docgen.service;

import com.docgen.dto.CreateTagRequest;
import com.docgen.dto.TagDTO;
import com.docgen.entity.TemplateTag;
import com.docgen.entity.TemplateTagMapping;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateTagRepository;
import com.docgen.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service handling template tag CRUD and template-tag associations.
 */
@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TemplateTagRepository tagRepository;
    private final TemplateTagMappingRepository tagMappingRepository;
    private final TemplateRepository templateRepository;

    public TagService(TemplateTagRepository tagRepository,
                      TemplateTagMappingRepository tagMappingRepository,
                      TemplateRepository templateRepository) {
        this.tagRepository = tagRepository;
        this.tagMappingRepository = tagMappingRepository;
        this.templateRepository = templateRepository;
    }

    @Transactional
    public TagDTO createTag(CreateTagRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();

        if (tagRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Tag name already exists for this tenant", HttpStatus.CONFLICT);
        }

        TemplateTag tag = new TemplateTag();
        tag.setTenantId(tenantId);
        tag.setName(request.getName());

        TemplateTag saved = tagRepository.save(tag);
        log.info("Tag created: name={}, tenantId={}", saved.getName(), tenantId);
        return toDTO(saved);
    }

    @Transactional
    public TagDTO updateTag(Long id, CreateTagRequest request) {
        TemplateTag tag = findTagOrThrow(id);
        Long tenantId = TenantContext.getCurrentTenantId();

        if (!tag.getName().equals(request.getName())
                && tagRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Tag name already exists for this tenant", HttpStatus.CONFLICT);
        }

        tag.setName(request.getName());
        TemplateTag saved = tagRepository.save(tag);
        log.info("Tag updated: id={}", saved.getId());
        return toDTO(saved);
    }

    @Transactional
    public void deleteTag(Long id) {
        TemplateTag tag = findTagOrThrow(id);
        // Remove all mappings for this tag
        List<TemplateTagMapping> mappings = tagMappingRepository.findByTagId(id);
        tagMappingRepository.deleteAll(mappings);
        tagRepository.delete(tag);
        log.info("Tag deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<TagDTO> listTags() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return tagRepository.findByTenantId(tenantId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TagDTO getTag(Long id) {
        return toDTO(findTagOrThrow(id));
    }

    @Transactional
    public void addTagToTemplate(Long templateId, Long tagId) {
        // Verify template exists
        templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "Template not found"));
        // Verify tag exists
        findTagOrThrow(tagId);

        if (tagMappingRepository.existsByTemplateIdAndTagId(templateId, tagId)) {
            return; // Already associated, idempotent
        }

        tagMappingRepository.save(new TemplateTagMapping(templateId, tagId));
        log.info("Tag {} added to template {}", tagId, templateId);
    }

    @Transactional
    public void removeTagFromTemplate(Long templateId, Long tagId) {
        tagMappingRepository.deleteByTemplateIdAndTagId(templateId, tagId);
        log.info("Tag {} removed from template {}", tagId, templateId);
    }

    @Transactional(readOnly = true)
    public List<TagDTO> getTagsForTemplate(Long templateId) {
        List<TemplateTagMapping> mappings = tagMappingRepository.findByTemplateId(templateId);
        return mappings.stream()
                .map(m -> tagRepository.findById(m.getTagId()).orElse(null))
                .filter(t -> t != null)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    private TemplateTag findTagOrThrow(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_FAILED, "Tag not found"));
    }

    private TagDTO toDTO(TemplateTag tag) {
        return new TagDTO(
                tag.getId(),
                tag.getTenantId(),
                tag.getName(),
                tag.getCreatedAt()
        );
    }
}
