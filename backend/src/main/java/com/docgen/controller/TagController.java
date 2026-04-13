package com.docgen.controller;

import com.docgen.dto.CreateTagRequest;
import com.docgen.dto.TagDTO;
import com.docgen.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for template tag management.
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    public ResponseEntity<TagDTO> createTag(@Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(request));
    }

    @GetMapping
    public ResponseEntity<List<TagDTO>> listTags() {
        return ResponseEntity.ok(tagService.listTags());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagDTO> getTag(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getTag(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagDTO> updateTag(
            @PathVariable Long id,
            @Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.ok(tagService.updateTag(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tagId}/templates/{templateId}")
    public ResponseEntity<Void> addTagToTemplate(
            @PathVariable Long tagId,
            @PathVariable Long templateId) {
        tagService.addTagToTemplate(templateId, tagId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{tagId}/templates/{templateId}")
    public ResponseEntity<Void> removeTagFromTemplate(
            @PathVariable Long tagId,
            @PathVariable Long templateId) {
        tagService.removeTagFromTemplate(templateId, tagId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/templates/{templateId}")
    public ResponseEntity<List<TagDTO>> getTagsForTemplate(@PathVariable Long templateId) {
        return ResponseEntity.ok(tagService.getTagsForTemplate(templateId));
    }
}
