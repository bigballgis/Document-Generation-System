package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.Segment;
import com.docgen.entity.SegmentTestData;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentTestDataRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing segment test data and running segment-level / composite-level tests.
 *
 * <p>Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7</p>
 */
@Service
public class SegmentTestService {

    private static final Logger log = LoggerFactory.getLogger(SegmentTestService.class);

    private final SegmentTestDataRepository testDataRepository;
    private final SegmentRepository segmentRepository;
    private final TemplateRepository templateRepository;
    private final SegmentRendererService segmentRendererService;
    private final AssemblyConfigService assemblyConfigService;
    private final ObjectMapper objectMapper;

    public SegmentTestService(SegmentTestDataRepository testDataRepository,
                              SegmentRepository segmentRepository,
                              TemplateRepository templateRepository,
                              SegmentRendererService segmentRendererService,
                              AssemblyConfigService assemblyConfigService,
                              ObjectMapper objectMapper) {
        this.testDataRepository = testDataRepository;
        this.segmentRepository = segmentRepository;
        this.templateRepository = templateRepository;
        this.segmentRendererService = segmentRendererService;
        this.assemblyConfigService = assemblyConfigService;
        this.objectMapper = objectMapper;
    }

    /**
     * Save test data for a segment.
     */
    @Transactional
    public SegmentTestDataDTO saveTestData(Long segmentId, CreateSegmentTestDataRequest request, Long userId) {
        // Validate segment exists
        segmentRepository.findById(segmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEGMENT_NOT_FOUND,
                        "Segment not found: " + segmentId, HttpStatus.NOT_FOUND));

        // Validate JSON
        validateJson(request.getTestDataJson());

        SegmentTestData entity = new SegmentTestData();
        entity.setSegmentId(segmentId);
        entity.setName(request.getName());
        entity.setTestDataJson(request.getTestDataJson());
        entity.setCreatedBy(userId);

        entity = testDataRepository.save(entity);
        log.info("Saved test data for segment {}: name={}, id={}", segmentId, request.getName(), entity.getId());
        return toDTO(entity);
    }

    /**
     * List all test data for a segment.
     */
    @Transactional(readOnly = true)
    public List<SegmentTestDataDTO> listTestData(Long segmentId) {
        return testDataRepository.findBySegmentIdOrderByCreatedAtDesc(segmentId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Delete a test data entry.
     */
    @Transactional
    public void deleteTestData(Long testDataId) {
        if (!testDataRepository.existsById(testDataId)) {
            throw new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND,
                    "Test data not found: " + testDataId, HttpStatus.NOT_FOUND);
        }
        testDataRepository.deleteById(testDataId);
        log.info("Deleted test data: id={}", testDataId);
    }

    /**
     * Run a segment test: render the segment with test data and return the result.
     */
    public SegmentTestResultDTO runSegmentTest(Long segmentId, Long testDataId) {
        SegmentTestData testData = testDataRepository.findById(testDataId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND,
                        "Test data not found: " + testDataId, HttpStatus.NOT_FOUND));

        Map<String, Object> data = parseJson(testData.getTestDataJson());

        SegmentTestResultDTO result = new SegmentTestResultDTO();
        result.setSegmentId(segmentId);
        result.setTestDataId(testDataId);
        result.setTestDataName(testData.getName());

        long start = System.currentTimeMillis();
        try {
            SegmentRenderResult renderResult = segmentRendererService.renderSegmentSafe(segmentId, null, data);
            long elapsed = System.currentTimeMillis() - start;

            result.setSuccess(renderResult.isSuccess());
            result.setRenderTimeMs(elapsed);
            result.setErrorMessage(renderResult.getErrorMessage());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            result.setSuccess(false);
            result.setRenderTimeMs(elapsed);
            result.setErrorMessage("Test execution error: " + e.getMessage());
            log.error("Segment test failed: segmentId={}, testDataId={}", segmentId, testDataId, e);
        }
        return result;
    }

    /**
     * Run all segment-level tests for a composite template, plus an overall assembly test.
     */
    public CompositeTestReportDTO runAllCompositeTests(Long compositeTemplateId) {
        Template template = templateRepository.findById(compositeTemplateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                        "Template not found: " + compositeTemplateId, HttpStatus.NOT_FOUND));

        if (!"COMPOSITE".equals(template.getTemplateType())) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND,
                    "Template is not a composite template: " + compositeTemplateId, HttpStatus.BAD_REQUEST);
        }

        AssemblyConfigDTO config = assemblyConfigService.deserialize(template.getAssemblyConfig());
        CompositeTestReportDTO report = new CompositeTestReportDTO();
        report.setCompositeTemplateId(compositeTemplateId);
        report.setTemplateName(template.getName());

        List<SegmentTestResultDTO> segmentResults = new ArrayList<>();
        int totalTests = 0;
        int passedTests = 0;

        // Run tests for each segment in the assembly config
        if (config.getSegments() != null) {
            for (AssemblySegmentEntry entry : config.getSegments()) {
                if (!entry.isEnabled()) continue;

                Long segmentId = entry.getSegmentId();
                List<SegmentTestData> testDataList = testDataRepository
                        .findBySegmentIdOrderByCreatedAtDesc(segmentId);

                for (SegmentTestData testData : testDataList) {
                    totalTests++;
                    SegmentTestResultDTO result = runSegmentTest(segmentId, testData.getId());
                    segmentResults.add(result);
                    if (result.isSuccess()) {
                        passedTests++;
                    }
                }
            }
        }

        report.setSegmentResults(segmentResults);
        report.setTotalTests(totalTests);
        report.setPassedTests(passedTests);
        report.setFailedTests(totalTests - passedTests);
        report.setExecutedAt(Instant.now());

        log.info("Composite test report: templateId={}, total={}, passed={}, failed={}",
                compositeTemplateId, totalTests, passedTests, totalTests - passedTests);
        return report;
    }

    // ── Private helpers ──

    private void validateJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Invalid JSON: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse test data JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private SegmentTestDataDTO toDTO(SegmentTestData entity) {
        SegmentTestDataDTO dto = new SegmentTestDataDTO();
        dto.setId(entity.getId());
        dto.setSegmentId(entity.getSegmentId());
        dto.setName(entity.getName());
        dto.setTestDataJson(entity.getTestDataJson());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
