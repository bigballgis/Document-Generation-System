package com.docgen.service;

import com.docgen.dto.MarketTemplateDTO;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.MarketTemplate;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateCategory;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.MarketTemplateRepository;
import com.docgen.repository.TemplateCategoryRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service handling template market operations: search, copy from market, and share to market.
 */
@Service
public class TemplateMarketService {

    private static final Logger log = LoggerFactory.getLogger(TemplateMarketService.class);

    private final MarketTemplateRepository marketTemplateRepository;
    private final TemplateRepository templateRepository;
    private final TemplateCategoryRepository categoryRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public TemplateMarketService(MarketTemplateRepository marketTemplateRepository,
                                 TemplateRepository templateRepository,
                                 TemplateCategoryRepository categoryRepository,
                                 MinioClient minioClient) {
        this.marketTemplateRepository = marketTemplateRepository;
        this.templateRepository = templateRepository;
        this.categoryRepository = categoryRepository;
        this.minioClient = minioClient;
    }

    /**
     * Search market templates visible to the current tenant.
     * Returns GLOBAL templates and TENANT_INTERNAL templates from the same tenant.
     */
    @Transactional(readOnly = true)
    public Page<MarketTemplateDTO> searchMarketTemplates(String keyword, Long categoryId, Pageable pageable) {
        Long tenantId = TenantContext.getCurrentTenantId();

        Page<MarketTemplate> page;
        if (categoryId != null) {
            page = marketTemplateRepository.searchMarketTemplatesWithCategory(tenantId, keyword, categoryId, pageable);
        } else {
            page = marketTemplateRepository.searchMarketTemplates(tenantId, keyword, pageable);
        }

        return page.map(this::toDTO);
    }

    /**
     * Copy a market template to the current user's workspace.
     * Creates an independent copy of the template with all configuration.
     */
    @Transactional
    public TemplateDTO copyFromMarket(Long marketTemplateId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        MarketTemplate marketTemplate = marketTemplateRepository.findById(marketTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.MARKET_TEMPLATE_NOT_FOUND, "市场模板不存在"));

        Template sourceTemplate = templateRepository.findById(marketTemplate.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "源模板不存在"));

        // Copy the template file in MinIO
        String copiedFilePath = copyTemplateFileInMinio(sourceTemplate.getTemplateFilePath(), tenantId);

        // Create independent copy
        Template copy = new Template();
        copy.setTenantId(tenantId);
        copy.setName(sourceTemplate.getName());
        copy.setDescription(sourceTemplate.getDescription());
        copy.setTemplateFilePath(copiedFilePath);
        copy.setOutputFormat(sourceTemplate.getOutputFormat());
        copy.setStorageStrategy(sourceTemplate.getStorageStrategy());
        copy.setAsync(sourceTemplate.isAsync());
        copy.setCreatedBy(tenantId); // Will be overridden by caller if needed
        copy.setReviewRequired(false);
        copy.setStatus("DRAFT");

        Template saved = templateRepository.save(copy);

        // Increment usage count
        marketTemplate.setUsageCount(marketTemplate.getUsageCount() + 1);
        marketTemplateRepository.save(marketTemplate);

        log.info("Template copied from market: marketTemplateId={}, newTemplateId={}", marketTemplateId, saved.getId());
        return toTemplateDTO(saved);
    }

    /**
     * Share a template to the market with the specified scope.
     */
    @Transactional
    public MarketTemplateDTO shareToMarket(Long templateId, String shareScope) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        if (marketTemplateRepository.existsByTemplateId(templateId)) {
            throw new BusinessException(ErrorCode.MARKET_TEMPLATE_ALREADY_SHARED,
                    "该模板已分享到市场", HttpStatus.CONFLICT);
        }

        MarketTemplate marketTemplate = new MarketTemplate();
        marketTemplate.setTemplateId(templateId);
        marketTemplate.setSharedBy(template.getCreatedBy());
        marketTemplate.setShareScope(shareScope);

        MarketTemplate saved = marketTemplateRepository.save(marketTemplate);
        log.info("Template shared to market: templateId={}, scope={}", templateId, shareScope);
        return toDTO(saved);
    }

    // ── Private helpers ──

    private String copyTemplateFileInMinio(String sourceFilePath, Long tenantId) {
        String destObjectName = String.format("templates/%d/%s_%s",
                tenantId, UUID.randomUUID(), extractFileName(sourceFilePath));
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(destObjectName)
                    .source(CopySource.builder()
                            .bucket(bucketName)
                            .object(sourceFilePath)
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("Failed to copy template file in MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "模板文件复制失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        return destObjectName;
    }

    private String extractFileName(String filePath) {
        if (filePath == null) return "template.docx";
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private MarketTemplateDTO toDTO(MarketTemplate mt) {
        Template template = templateRepository.findById(mt.getTemplateId()).orElse(null);
        String name = template != null ? template.getName() : null;
        String description = template != null ? template.getDescription() : null;
        String categoryName = null;
        if (template != null && template.getCategoryId() != null) {
            categoryName = categoryRepository.findById(template.getCategoryId())
                    .map(TemplateCategory::getName)
                    .orElse(null);
        }
        return new MarketTemplateDTO(
                mt.getId(),
                mt.getTemplateId(),
                name,
                description,
                categoryName,
                mt.getShareScope(),
                mt.getSharedBy(),
                mt.getUsageCount(),
                mt.getRating(),
                mt.getCreatedAt()
        );
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
