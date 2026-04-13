package com.docgen.service;

import com.docgen.dto.CategoryDTO;
import com.docgen.dto.CreateCategoryRequest;
import com.docgen.entity.TemplateCategory;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateCategoryRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service handling template category CRUD and tree management.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final TemplateCategoryRepository categoryRepository;
    private final TemplateRepository templateRepository;

    public CategoryService(TemplateCategoryRepository categoryRepository,
                           TemplateRepository templateRepository) {
        this.categoryRepository = categoryRepository;
        this.templateRepository = templateRepository;
    }

    @Transactional
    public CategoryDTO createCategory(CreateCategoryRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();

        if (categoryRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "分类名称在该租户下已存在", HttpStatus.CONFLICT);
        }

        if (request.getParentId() != null) {
            categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.VALIDATION_FAILED, "父分类不存在"));
        }

        TemplateCategory category = new TemplateCategory();
        category.setTenantId(tenantId);
        category.setName(request.getName());
        category.setParentId(request.getParentId());
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        TemplateCategory saved = categoryRepository.save(category);
        log.info("Category created: name={}, tenantId={}", saved.getName(), tenantId);
        return toDTO(saved);
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CreateCategoryRequest request) {
        TemplateCategory category = findCategoryOrThrow(id);

        if (request.getName() != null && !request.getName().equals(category.getName())) {
            Long tenantId = TenantContext.getCurrentTenantId();
            if (categoryRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "分类名称在该租户下已存在", HttpStatus.CONFLICT);
            }
            category.setName(request.getName());
        }
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "分类不能作为自身的子分类", HttpStatus.BAD_REQUEST);
            }
            category.setParentId(request.getParentId());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }

        TemplateCategory saved = categoryRepository.save(category);
        log.info("Category updated: id={}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Delete a category. Moves templates under this category to default (null categoryId).
     * Also reassigns child categories to the deleted category's parent.
     */
    @Transactional
    public void deleteCategory(Long id) {
        TemplateCategory category = findCategoryOrThrow(id);

        // Move templates in this category to default (null)
        templateRepository.findByCategoryId(id).forEach(template -> {
            template.setCategoryId(null);
            templateRepository.save(template);
        });

        // Reassign child categories to the deleted category's parent
        List<TemplateCategory> children = categoryRepository.findByParentId(id);
        for (TemplateCategory child : children) {
            child.setParentId(category.getParentId());
            categoryRepository.save(child);
        }

        categoryRepository.delete(category);
        log.info("Category deleted: id={}", id);
    }

    /**
     * Get the full category tree for the current tenant.
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getCategoryTree() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<TemplateCategory> allCategories = categoryRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        return buildTree(allCategories);
    }

    @Transactional(readOnly = true)
    public CategoryDTO getCategory(Long id) {
        return toDTO(findCategoryOrThrow(id));
    }

    // ── Private helpers ──

    private TemplateCategory findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_FAILED, "分类不存在"));
    }

    /**
     * Build a tree structure from a flat list of categories.
     */
    List<CategoryDTO> buildTree(List<TemplateCategory> categories) {
        Map<Long, CategoryDTO> dtoMap = new LinkedHashMap<>();
        for (TemplateCategory cat : categories) {
            dtoMap.put(cat.getId(), toDTO(cat));
        }

        List<CategoryDTO> roots = new ArrayList<>();
        for (CategoryDTO dto : dtoMap.values()) {
            if (dto.getParentId() == null) {
                roots.add(dto);
            } else {
                CategoryDTO parent = dtoMap.get(dto.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                } else {
                    // Parent not found (possibly filtered out), treat as root
                    roots.add(dto);
                }
            }
        }
        return roots;
    }

    private CategoryDTO toDTO(TemplateCategory category) {
        return new CategoryDTO(
                category.getId(),
                category.getTenantId(),
                category.getParentId(),
                category.getName(),
                category.getSortOrder(),
                category.getCreatedAt()
        );
    }
}
