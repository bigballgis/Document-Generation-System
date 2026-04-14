package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.MarketTemplateDTO;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.MarketTemplate;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.MarketTemplateRepository;
import com.docgen.repository.TemplateCategoryRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service handling composite template market operations:
 * copying composite templates from market and sharing to market.
 */
@Service
public class CompositeMarketService {

    private static final Logger log = LoggerFactory.getLogger(CompositeMarketService.class);

    private final MarketTemplateRepository marketTemplateRepository;
    private final TemplateRepository templateRepository;
    private final TemplateCategoryRepository categoryRepository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public CompositeMarketService(MarketTemplateRepository marketTemplateRepository,
                                  TemplateRepository templateRepository,
                                  TemplateCategoryRepository categoryRepository,
                                  MinioClient minioClient,
                                  ObjectMapper objectMapper) {
        this.marketTemplateRepository = marketTemplateRepository;
        this.templateRepository = templateRepository;
        this.categoryRepository = categoryRepository;
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Copy a composite template from the market, creating a fully independent copy.
     * All segment .docx files referenced by inline filePath are copied in MinIO.
     */
    @Transactional
    public TemplateDTO copyCompositeFromMarket(Long marketTemplateId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        MarketTemplate marketTemplate = marketTemplateRepository.findById(marketTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.MARKET_TEMPLATE_NOT_FOUND, "市场模板不存在"));

        Template sourceTemplate = templateRepository.findById(marketTemplate.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "源模板不存在"));

        if (!"COMPOSITE".equals(sourceTemplate.getTemplateType())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "该市场模板不是组合模板", HttpStatus.BAD_REQUEST);
        }

        // Parse the source assembly config
        AssemblyConfigDTO sourceConfig = parseAssemblyConfig(sourceTemplate.getAssemblyConfig());

        // Copy each segment's file in MinIO and build new assembly config with copied filePaths
        String newAssemblyConfig = copyAndRemapAssemblyConfig(sourceConfig, tenantId);

        // Create the composite template copy
        Template copy = new Template();
        copy.setTenantId(tenantId);
        copy.setName(sourceTemplate.getName());
        copy.setDescription(sourceTemplate.getDescription());
        copy.setTemplateFilePath(sourceTemplate.getTemplateFilePath() != null
                ? copyFileInMinio(sourceTemplate.getTemplateFilePath(), tenantId) : "");
        copy.setOutputFormat(sourceTemplate.getOutputFormat());
        copy.setStorageStrategy(sourceTemplate.getStorageStrategy());
        copy.setAsync(sourceTemplate.isAsync());
        copy.setCreatedBy(tenantId);
        copy.setReviewRequired(false);
        copy.setStatus("DRAFT");
        copy.setTemplateType("COMPOSITE");
        copy.setAssemblyConfig(newAssemblyConfig);

        Template saved = templateRepository.save(copy);

        // Increment usage count
        marketTemplate.setUsageCount(marketTemplate.getUsageCount() + 1);
        marketTemplateRepository.save(marketTemplate);

        log.info("Composite template copied from market: marketTemplateId={}, newTemplateId={}",
                marketTemplateId, saved.getId());

        return toTemplateDTO(saved);
    }

    /**
     * Share a composite template to the market.
     */
    @Transactional
    public MarketTemplateDTO shareCompositeToMarket(Long templateId, String shareScope) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        if (!"COMPOSITE".equals(template.getTemplateType())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "只有组合模板可以分享到市场", HttpStatus.BAD_REQUEST);
        }

        if (marketTemplateRepository.existsByTemplateId(templateId)) {
            throw new BusinessException(ErrorCode.MARKET_TEMPLATE_ALREADY_SHARED,
                    "该模板已分享到市场", HttpStatus.CONFLICT);
        }

        MarketTemplate marketTemplate = new MarketTemplate();
        marketTemplate.setTemplateId(templateId);
        marketTemplate.setSharedBy(template.getCreatedBy());
        marketTemplate.setShareScope(shareScope);

        MarketTemplate saved = marketTemplateRepository.save(marketTemplate);
        log.info("Composite template shared to market: templateId={}, scope={}", templateId, shareScope);
        return toMarketTemplateDTO(saved);
    }

    // ── Private helpers ──

    /**
     * Copy segment files in MinIO and remap assembly config to use new filePaths.
     */
    private String copyAndRemapAssemblyConfig(AssemblyConfigDTO config, Long tenantId) {
        if (config == null || config.getSegments() == null) return null;

        AssemblyConfigDTO newConfig = new AssemblyConfigDTO();
        List<AssemblySegmentEntry> newEntries = new ArrayList<>();

        for (AssemblySegmentEntry entry : config.getSegments()) {
            AssemblySegmentEntry newEntry = new AssemblySegmentEntry();

            // Copy the file in MinIO if filePath exists
            String copiedFilePath = "";
            if (entry.getFilePath() != null && !entry.getFilePath().isBlank()) {
                copiedFilePath = copyFileInMinio(entry.getFilePath(), tenantId);
            }

            newEntry.setFilePath(copiedFilePath);
            newEntry.setName(entry.getName());
            newEntry.setSegmentType(entry.getSegmentType());
            newEntry.setPosition(entry.getPosition());
            newEntry.setEnabled(entry.isEnabled());
            newEntry.setPageBreakBefore(entry.isPageBreakBefore());
            newEntry.setConditionExpression(entry.getConditionExpression());
            newEntry.setDataScope(entry.getDataScope());
            newEntries.add(newEntry);
        }

        newConfig.setSegments(newEntries);
        try {
            return objectMapper.writeValueAsString(newConfig);
        } catch (Exception e) {
            log.error("Failed to serialize assembly_config: {}", e.getMessage());
            return null;
        }
    }

    private String copyFileInMinio(String sourceFilePath, Long tenantId) {
        if (sourceFilePath == null || sourceFilePath.isBlank()) return "";
        String destObjectName = String.format("segments/%d/%s_%s",
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
            log.error("Failed to copy file in MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "文件复制失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        return destObjectName;
    }

    private String extractFileName(String filePath) {
        if (filePath == null) return "file.docx";
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private AssemblyConfigDTO parseAssemblyConfig(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, AssemblyConfigDTO.class);
        } catch (Exception e) {
            log.warn("Failed to parse assembly_config: {}", e.getMessage());
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

    private MarketTemplateDTO toMarketTemplateDTO(MarketTemplate mt) {
        Template template = templateRepository.findById(mt.getTemplateId()).orElse(null);
        String name = template != null ? template.getName() : null;
        String description = template != null ? template.getDescription() : null;
        String categoryName = null;
        if (template != null && template.getCategoryId() != null) {
            categoryName = categoryRepository.findById(template.getCategoryId())
                    .map(cat -> cat.getName())
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
}
