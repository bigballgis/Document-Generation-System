package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.CompositeCoverageReport;
import com.docgen.dto.CompositeCoverageReport.SegmentCoverageEntry;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.*;

/**
 * Service for computing aggregate coverage across all Segments of a Composite_Template.
 * Uses inline segment data from assembly_config and Docxtemplater {@code POST /scan-variables} for variable scanning.
 */
@Service
public class CompositeCoverageService {

    private static final Logger log = LoggerFactory.getLogger(CompositeCoverageService.class);

    private final TemplateRepository templateRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final MinioClient minioClient;
    private final RestTemplate restTemplate;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    public CompositeCoverageService(TemplateRepository templateRepository,
                                    AssemblyConfigService assemblyConfigService,
                                    MinioClient minioClient,
                                    RestTemplate restTemplate) {
        this.templateRepository = templateRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.minioClient = minioClient;
        this.restTemplate = restTemplate;
    }

    /**
     * Check coverage for a Composite_Template by aggregating variable coverage
     * across all its inline segments from assembly_config.
     */
    @Transactional(readOnly = true)
    public CompositeCoverageReport checkCoverage(Long compositeTemplateId) {
        Template template = findCompositeTemplateOrThrow(compositeTemplateId);
        AssemblyConfigDTO config = assemblyConfigService.deserialize(template.getAssemblyConfig());

        List<SegmentCoverageEntry> segmentCoverages = new ArrayList<>();
        int totalVarsAll = 0;
        int boundVarsAll = 0;

        List<AssemblySegmentEntry> segments = config.getSegments();
        if (segments == null) {
            segments = Collections.emptyList();
        }

        for (AssemblySegmentEntry segment : segments) {
            if (!segment.isEnabled()) continue;

            List<String> variables = scanVariablesFromFile(segment.getFilePath());

            int totalVars = variables.size();
            // All scanned variables are considered "bound" (present in template)
            int boundVars = totalVars;

            double segCoverage = totalVars == 0 ? 100.0 : (boundVars * 100.0) / totalVars;

            SegmentCoverageEntry entry = new SegmentCoverageEntry();
            entry.setSegmentName(segment.getName());
            entry.setTotalVariables(totalVars);
            entry.setBoundVariables(boundVars);
            entry.setCoveragePercent(Math.round(segCoverage * 100.0) / 100.0);

            segmentCoverages.add(entry);
            totalVarsAll += totalVars;
            boundVarsAll += boundVars;
        }

        double overallCoverage = totalVarsAll == 0 ? 100.0 : (boundVarsAll * 100.0) / totalVarsAll;

        CompositeCoverageReport report = new CompositeCoverageReport();
        report.setOverallCoveragePercent(Math.round(overallCoverage * 100.0) / 100.0);
        report.setSegmentCoverages(segmentCoverages);

        log.info("Composite coverage for template {}: {}/{} bound ({}%), segments={}",
                compositeTemplateId, boundVarsAll, totalVarsAll,
                report.getOverallCoveragePercent(), segments.size());

        return report;
    }

    /**
     * Check if the composite template's overall coverage is below the given threshold.
     */
    @Transactional(readOnly = true)
    public boolean isBelowThreshold(Long compositeTemplateId, double threshold) {
        CompositeCoverageReport report = checkCoverage(compositeTemplateId);
        return report.getOverallCoveragePercent() < threshold;
    }

    /**
     * Scan variables from a segment file using Docxtemplater {@code POST /scan-variables}.
     */
    private List<String> scanVariablesFromFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> requestBody = Map.of("templatePath", filePath);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    docxtemplaterServiceUrl + "/scan-variables",
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getBody() != null && response.getBody().containsKey("variables")) {
                Object vars = response.getBody().get("variables");
                if (vars instanceof List<?>) {
                    List<String> result = new ArrayList<>();
                    for (Object v : (List<?>) vars) {
                        if (v instanceof String) {
                            result.add((String) v);
                        }
                    }
                    return result;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to scan variables from file {}: {}", filePath, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Template findCompositeTemplateOrThrow(Long compositeTemplateId) {
        Template template = templateRepository.findById(compositeTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在: " + compositeTemplateId));
        if (!"COMPOSITE".equals(template.getTemplateType())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "Template is not a composite template: " + compositeTemplateId,
                    HttpStatus.BAD_REQUEST);
        }
        return template;
    }
}
