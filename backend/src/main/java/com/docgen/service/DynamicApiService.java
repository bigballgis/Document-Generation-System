package com.docgen.service;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service handling dynamic API endpoint logic for activated templates.
 * When a template is activated, it becomes callable via /api/generate/{templateId}.
 * Supports version selection via ?version={versionNumber} query parameter.
 */
@Service
public class DynamicApiService {

    private static final Logger log = LoggerFactory.getLogger(DynamicApiService.class);

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final DocumentGeneratorService documentGeneratorService;

    public DynamicApiService(TemplateRepository templateRepository,
                             TemplateVersionRepository templateVersionRepository,
                             DocumentGeneratorService documentGeneratorService) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.documentGeneratorService = documentGeneratorService;
    }

    /**
     * Generate a document via the dynamic API endpoint.
     * Only ACTIVE templates can be called. Supports version selection.
     *
     * @param templateId the template ID
     * @param version    optional version number; null means latest active version
     * @param request    the generation request with parameters
     * @return the generated document response
     */
    public GenerateDocumentResponse generateViaApi(Long templateId, Integer version,
                                                    GenerateDocumentRequest request) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "模板不存在: " + templateId, HttpStatus.NOT_FOUND));

        // Only ACTIVE templates expose API endpoints
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "模板未激活，无法通过 API 调用: " + templateId, HttpStatus.NOT_FOUND);
        }

        if (version != null) {
            // Check if history versions are allowed
            Optional<Integer> latestVersion = templateVersionRepository.findMaxVersionNumber(templateId);
            boolean isLatest = latestVersion.isPresent() && latestVersion.get().equals(version);

            if (!template.isAllowHistoryVersions() && !isLatest) {
                throw new BusinessException(ErrorCode.GENERATE_VERSION_NOT_ALLOWED,
                        "该模板不允许调用历史版本，请使用最新版本", HttpStatus.BAD_REQUEST);
            }

            // Verify the requested version exists
            templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)
                    .stream()
                    .filter(v -> v.getVersionNumber().equals(version))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_VERSION_NOT_FOUND,
                            "模板版本不存在: " + version, HttpStatus.NOT_FOUND));

            log.info("Generating document via API for template {} version {}", templateId, version);
        } else {
            log.info("Generating document via API for template {} (latest version)", templateId);
        }

        return documentGeneratorService.generateDocument(templateId, request);
    }
}
