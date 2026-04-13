package com.docgen.service;

import com.docgen.dto.CategoryDTO;
import com.docgen.dto.CreateCategoryRequest;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateCategory;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateCategoryRepository;
import com.docgen.repository.TemplateRepository;
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
class CategoryServiceTest {

    @Mock
    private TemplateCategoryRepository categoryRepository;

    @Mock
    private TemplateRepository templateRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, templateRepository);
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCategory_success() {
        CreateCategoryRequest request = new CreateCategoryRequest("Reports", null, 0);

        when(categoryRepository.existsByTenantIdAndName(1L, "Reports")).thenReturn(false);
        when(categoryRepository.save(any(TemplateCategory.class))).thenAnswer(inv -> {
            TemplateCategory c = inv.getArgument(0);
            c.setId(1L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CategoryDTO result = categoryService.createCategory(request);

        assertEquals("Reports", result.getName());
        assertEquals(1L, result.getTenantId());
        assertNull(result.getParentId());
    }

    @Test
    void createCategory_duplicateName_throws() {
        CreateCategoryRequest request = new CreateCategoryRequest("Reports", null, 0);
        when(categoryRepository.existsByTenantIdAndName(1L, "Reports")).thenReturn(true);

        assertThrows(BusinessException.class, () -> categoryService.createCategory(request));
    }

    @Test
    void createCategory_withParent_success() {
        TemplateCategory parent = createTestCategory(1L, "Parent", null);
        when(categoryRepository.existsByTenantIdAndName(1L, "Child")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(TemplateCategory.class))).thenAnswer(inv -> {
            TemplateCategory c = inv.getArgument(0);
            c.setId(2L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CreateCategoryRequest request = new CreateCategoryRequest("Child", 1L, 1);
        CategoryDTO result = categoryService.createCategory(request);

        assertEquals("Child", result.getName());
        assertEquals(1L, result.getParentId());
    }

    @Test
    void createCategory_parentNotFound_throws() {
        when(categoryRepository.existsByTenantIdAndName(1L, "Child")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        CreateCategoryRequest request = new CreateCategoryRequest("Child", 99L, 0);
        assertThrows(ResourceNotFoundException.class, () -> categoryService.createCategory(request));
    }

    @Test
    void deleteCategory_movesTemplatesAndReassignsChildren() {
        TemplateCategory category = createTestCategory(1L, "ToDelete", null);
        Template template = new Template();
        template.setId(10L);
        template.setCategoryId(1L);

        TemplateCategory child = createTestCategory(2L, "Child", 1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(templateRepository.findByCategoryId(1L)).thenReturn(List.of(template));
        when(categoryRepository.findByParentId(1L)).thenReturn(List.of(child));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.save(any(TemplateCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.deleteCategory(1L);

        assertNull(template.getCategoryId());
        assertNull(child.getParentId());
        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategory_notFound_throws() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(99L));
    }

    @Test
    void getCategoryTree_buildsHierarchy() {
        TemplateCategory root = createTestCategory(1L, "Root", null);
        TemplateCategory child1 = createTestCategory(2L, "Child1", 1L);
        TemplateCategory child2 = createTestCategory(3L, "Child2", 1L);
        TemplateCategory grandchild = createTestCategory(4L, "Grandchild", 2L);

        when(categoryRepository.findByTenantIdOrderBySortOrderAsc(1L))
                .thenReturn(List.of(root, child1, child2, grandchild));

        List<CategoryDTO> tree = categoryService.getCategoryTree();

        assertEquals(1, tree.size());
        assertEquals("Root", tree.get(0).getName());
        assertEquals(2, tree.get(0).getChildren().size());
        assertEquals("Child1", tree.get(0).getChildren().get(0).getName());
        assertEquals(1, tree.get(0).getChildren().get(0).getChildren().size());
        assertEquals("Grandchild", tree.get(0).getChildren().get(0).getChildren().get(0).getName());
    }

    @Test
    void updateCategory_success() {
        TemplateCategory category = createTestCategory(1L, "Old", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByTenantIdAndName(1L, "New")).thenReturn(false);
        when(categoryRepository.save(any(TemplateCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCategoryRequest request = new CreateCategoryRequest("New", null, 5);
        CategoryDTO result = categoryService.updateCategory(1L, request);

        assertEquals("New", result.getName());
    }

    @Test
    void updateCategory_selfParent_throws() {
        TemplateCategory category = createTestCategory(1L, "Cat", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setParentId(1L);

        assertThrows(BusinessException.class, () -> categoryService.updateCategory(1L, request));
    }

    // ── Helper ──

    private TemplateCategory createTestCategory(Long id, String name, Long parentId) {
        TemplateCategory cat = new TemplateCategory();
        cat.setId(id);
        cat.setTenantId(1L);
        cat.setName(name);
        cat.setParentId(parentId);
        cat.setSortOrder(0);
        cat.setCreatedAt(Instant.now());
        return cat;
    }
}
