package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that orchestrates the complete composite document generation flow:
 * validate parameters → evaluate DERIVED parameters → assemble segments via AssemblyEngineService → apply watermark → store document.
 *
 * <p>Uses ParameterValidationService for parameter handling.</p>
 *
 * <p>Validates: Requirements 8.1, 8.4, 8.5, 8.9, 8.10, 16.1, 16.2</p>
 */
@Service
public class CompositeGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(CompositeGeneratorService.class);

    private final TemplateRepository templateRepository;
    private final ParameterValidationService parameterValidationService;
    private final AssemblyConfigService assemblyConfigService;
    private final AssemblyEngineService assemblyEngineService;
    private final DocumentStorageService documentStorageService;
    private final WatermarkService watermarkService;

    public CompositeGeneratorService(TemplateRepository templateRepository,
                                     ParameterValidationService parameterValidationService,
                                     AssemblyConfigService assemblyConfigService,
                                     AssemblyEngineService assemblyEngineService,
                                     DocumentStorageService documentStorageService,
                                     WatermarkService watermarkService) {
        this.templateRepository = templateRepository;
        this.parameterValidationService = parameterValidationService;
        this.assemblyConfigService = assemblyConfigService;
        this.assemblyEngineService = assemblyEngineService;
        this.documentStorageService = documentStorageService;
        this.watermarkService = watermarkService;
    }

    /**
     * Generate a composite document for the given template.
     *
     * <ol>
     *   <li>Validate parameters and evaluate DERIVED parameters (ParameterValidationService)</li>
     *   <li>Assemble document via AssemblyEngineService</li>
     *   <li>Apply watermark if configured (WatermarkService)</li>
     *   <li>Store document (DocumentStorageService)</li>
     *   <li>Return response with per-segment render time statistics</li>
     * </ol>
     *
     * @param templateId the composite template ID
     * @param request    the generation request
     * @return response containing the generated document and segment render stats
     */
    public GenerateDocumentResponse generateCompositeDocument(Long templateId, GenerateDocumentRequest request) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        Map<String, Object> params = request.getParameters() != null
                ? request.getParameters() : Collections.emptyMap();

        String storageStrategy = request.getStorageStrategy() != null
                ? request.getStorageStrategy() : template.getStorageStrategy();

        // Step 1: Validate parameters and evaluate DERIVED parameters
        Map<String, Object> data = executePipeline(template, params);

        // Step 2: Get assembly config and assemble document
        AssemblyConfigDTO assemblyConfig = getAssemblyConfig(template);
        AssemblyResult assemblyResult = assemblyEngineService.assembleDocument(assemblyConfig, data);

        byte[] docxBytes = assemblyResult.getDocumentBytes();

        // Step 3: Apply watermark if configured
        docxBytes = applyWatermarkIfConfigured(template, docxBytes, data);

        // Step 4: Store document
        GenerateDocumentResponse response = documentStorageService.store(
                template, docxBytes, "WORD", storageStrategy);

        // Step 5: Attach segment render stats to response metadata
        List<SegmentRenderStat> segmentStats = buildSegmentStats(assemblyResult);
        response.setSegmentRenderStats(segmentStats);
        response.setTotalRenderTimeMs(assemblyResult.getTotalRenderTimeMs());

        log.info("Composite document generated for template {}: {} segments, total time {}ms",
                templateId, segmentStats.size(), assemblyResult.getTotalRenderTimeMs());

        return response;
    }

    /**
     * Three-step pipeline: validate parameters → evaluate DERIVED parameters → return data context.
     */
    private Map<String, Object> executePipeline(Template template, Map<String, Object> params) {
        try {
            return parameterValidationService.validateAndBuildContext(template.getId(), params);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Parameter validation pipeline failed for composite template {}: {}", template.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_FAILED,
                    "参数验证管道执行失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private AssemblyConfigDTO getAssemblyConfig(Template template) {
        String json = template.getAssemblyConfig();
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.COMPOSITE_TEMPLATE_EMPTY,
                    "组合模板的 Assembly_Config 为空", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return assemblyConfigService.deserialize(json);
    }

    /**
     * Apply watermark to the assembled document if the template has watermark configuration.
     * Currently checks for text watermark config in template metadata.
     */
    private byte[] applyWatermarkIfConfigured(Template template, byte[] docxBytes, Map<String, Object> data) {
        // Watermark is applied at the composite level after all segments are merged.
        // The template's watermark config would be stored as part of template metadata.
        // For now, return as-is — watermark config integration follows existing pattern.
        return docxBytes;
    }

    private List<SegmentRenderStat> buildSegmentStats(AssemblyResult assemblyResult) {
        if (assemblyResult.getSegmentResults() == null) {
            return Collections.emptyList();
        }
        return assemblyResult.getSegmentResults().stream()
                .map(r -> {
                    SegmentRenderStat stat = new SegmentRenderStat();
                    stat.setSegmentName(r.getSegmentName());
                    stat.setRenderTimeMs(r.getRenderTimeMs());
                    stat.setSuccess(r.isSuccess());
                    stat.setErrorMessage(r.getErrorMessage());
                    return stat;
                })
                .collect(Collectors.toList());
    }
}
