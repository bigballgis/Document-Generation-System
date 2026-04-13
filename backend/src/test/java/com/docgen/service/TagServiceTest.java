package com.docgen.service;

import com.docgen.dto.CreateTagRequest;
import com.docgen.dto.TagDTO;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateTag;
import com.docgen.entity.TemplateTagMapping;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateTagRepository;
import com.docgen.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TemplateTagRepository tagRepository;

    @Mock
    private TemplateTagMappingRepository tagMappingRepository;

    @Mock
    private TemplateRepository templateRepository;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, tagMappingRepository, templateRepository);
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createTag_success() {
        when(tagRepository.existsByTenantIdAndName(1L, "urgent")).thenReturn(false);
        when(tagRepository.save(any(TemplateTag.class))).thenAnswer(inv -> {
            TemplateTag t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(Instant.now());
            return t;
        });

        TagDTO result = tagService.createTag(new CreateTagRequest("urgent"));

        assertEquals("urgent", result.getName());
        assertEquals(1L, result.getTenantId());
    }

    @Test
    void createTag_duplicateName_throws() {
        when(tagRepository.existsByTenantIdAndName(1L, "urgent")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> tagService.createTag(new CreateTagRequest("urgent")));
    }

    @Test
    void updateTag_success() {
        TemplateTag tag = createTestTag(1L, "old");
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByTenantIdAndName(1L, "new")).thenReturn(false);
        when(tagRepository.save(any(TemplateTag.class))).thenAnswer(inv -> inv.getArgument(0));

        TagDTO result = tagService.updateTag(1L, new CreateTagRequest("new"));

        assertEquals("new", result.getName());
    }

    @Test
    void updateTag_notFound_throws() {
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.updateTag(99L, new CreateTagRequest("new")));
    }

    @Test
    void deleteTag_removesAllMappings() {
        TemplateTag tag = createTestTag(1L, "toDelete");
        TemplateTagMapping mapping = new TemplateTagMapping(10L, 1L);

        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(tagMappingRepository.findByTagId(1L)).thenReturn(List.of(mapping));

        tagService.deleteTag(1L);

        verify(tagMappingRepository).deleteAll(List.of(mapping));
        verify(tagRepository).delete(tag);
    }

    @Test
    void listTags_returnsTenantTags() {
        TemplateTag tag1 = createTestTag(1L, "tag1");
        TemplateTag tag2 = createTestTag(2L, "tag2");
        when(tagRepository.findByTenantId(1L)).thenReturn(List.of(tag1, tag2));

        List<TagDTO> result = tagService.listTags();

        assertEquals(2, result.size());
    }

    @Test
    void addTagToTemplate_success() {
        Template template = new Template();
        template.setId(10L);
        TemplateTag tag = createTestTag(1L, "tag");

        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(tagMappingRepository.existsByTemplateIdAndTagId(10L, 1L)).thenReturn(false);
        when(tagMappingRepository.save(any(TemplateTagMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        tagService.addTagToTemplate(10L, 1L);

        verify(tagMappingRepository).save(any(TemplateTagMapping.class));
    }

    @Test
    void addTagToTemplate_alreadyExists_idempotent() {
        Template template = new Template();
        template.setId(10L);
        TemplateTag tag = createTestTag(1L, "tag");

        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(tagMappingRepository.existsByTemplateIdAndTagId(10L, 1L)).thenReturn(true);

        tagService.addTagToTemplate(10L, 1L);

        verify(tagMappingRepository, never()).save(any());
    }

    @Test
    void addTagToTemplate_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> tagService.addTagToTemplate(99L, 1L));
    }

    @Test
    void removeTagFromTemplate_success() {
        tagService.removeTagFromTemplate(10L, 1L);

        verify(tagMappingRepository).deleteByTemplateIdAndTagId(10L, 1L);
    }

    @Test
    void getTagsForTemplate_success() {
        TemplateTagMapping mapping1 = new TemplateTagMapping(10L, 1L);
        TemplateTagMapping mapping2 = new TemplateTagMapping(10L, 2L);
        TemplateTag tag1 = createTestTag(1L, "tag1");
        TemplateTag tag2 = createTestTag(2L, "tag2");

        when(tagMappingRepository.findByTemplateId(10L)).thenReturn(List.of(mapping1, mapping2));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag1));
        when(tagRepository.findById(2L)).thenReturn(Optional.of(tag2));

        List<TagDTO> result = tagService.getTagsForTemplate(10L);

        assertEquals(2, result.size());
    }

    // ── Helper ──

    private TemplateTag createTestTag(Long id, String name) {
        TemplateTag tag = new TemplateTag();
        tag.setId(id);
        tag.setTenantId(1L);
        tag.setName(name);
        tag.setCreatedAt(Instant.now());
        return tag;
    }
}
