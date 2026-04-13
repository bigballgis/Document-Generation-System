package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.*;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TestCaseRepository;
import com.docgen.repository.TestResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing template test cases and executing tests.
 * Supports three comparison strategies: variable-value, text-content, and file-snapshot.
 */
@Service
public class TemplateTestService {

    private static final Logger log = LoggerFactory.getLogger(TemplateTestService.class);

    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final ObjectMapper objectMapper;

    public TemplateTestService(TestCaseRepository testCaseRepository,
                               TestResultRepository testResultRepository,
                               ObjectMapper objectMapper) {
        this.testCaseRepository = testCaseRepository;
        this.testResultRepository = testResultRepository;
        this.objectMapper = objectMapper;
    }

    // ── CRUD operations ──

    @Transactional
    public TestCaseDTO createTestCase(Long templateId, CreateTestCaseRequest request) {
        TestCase testCase = new TestCase();
        testCase.setTemplateId(templateId);
        testCase.setName(request.getName());
        testCase.setTestDataJson(request.getTestDataJson());
        testCase.setExpectedResultJson(request.getExpectedResultJson());
        testCase.setComparisonType(
                request.getComparisonType() != null ? request.getComparisonType() : ComparisonType.VARIABLE_VALUE);
        testCase = testCaseRepository.save(testCase);
        return toTestCaseDTO(testCase);
    }

    @Transactional(readOnly = true)
    public List<TestCaseDTO> listTestCases(Long templateId) {
        return testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId)
                .stream()
                .map(this::toTestCaseDTO)
                .toList();
    }

    @Transactional
    public TestCaseDTO updateTestCase(Long testCaseId, CreateTestCaseRequest request) {
        TestCase testCase = findTestCaseOrThrow(testCaseId);
        if (request.getName() != null) {
            testCase.setName(request.getName());
        }
        if (request.getTestDataJson() != null) {
            testCase.setTestDataJson(request.getTestDataJson());
        }
        if (request.getExpectedResultJson() != null) {
            testCase.setExpectedResultJson(request.getExpectedResultJson());
        }
        if (request.getComparisonType() != null) {
            testCase.setComparisonType(request.getComparisonType());
        }
        testCase = testCaseRepository.save(testCase);
        return toTestCaseDTO(testCase);
    }

    @Transactional
    public void deleteTestCase(Long testCaseId) {
        TestCase testCase = findTestCaseOrThrow(testCaseId);
        testCaseRepository.delete(testCase);
    }

    // ── Test execution ──

    @Transactional
    public TestResultDTO runTestCase(Long testCaseId) {
        TestCase testCase = findTestCaseOrThrow(testCaseId);
        try {
            Map<String, Object> testData = parseJson(testCase.getTestDataJson());
            Map<String, Object> expectedResult = testCase.getExpectedResultJson() != null
                    ? parseJson(testCase.getExpectedResultJson()) : Collections.emptyMap();

            // Simulate document generation with test data and compare results
            ComparisonResult comparison = compare(testCase.getComparisonType(), testData, expectedResult);

            TestResult result = new TestResult();
            result.setTestCaseId(testCaseId);
            result.setStatus(comparison.passed() ? TestStatus.PASSED : TestStatus.FAILED);
            result.setActualResultJson(objectMapper.writeValueAsString(testData));
            result.setDiffDetails(comparison.diffDetails());
            result.setExecutedAt(Instant.now());
            result = testResultRepository.save(result);
            return toTestResultDTO(result);
        } catch (Exception e) {
            log.error("Test case execution failed for testCaseId={}: {}", testCaseId, e.getMessage());
            TestResult result = new TestResult();
            result.setTestCaseId(testCaseId);
            result.setStatus(TestStatus.FAILED);
            result.setDiffDetails("Execution error: " + e.getMessage());
            result.setExecutedAt(Instant.now());
            result = testResultRepository.save(result);
            return toTestResultDTO(result);
        }
    }

    @Transactional
    public TestReportDTO runAllTests(Long templateId) {
        List<TestCase> testCases = testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
        List<TestResultDTO> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        for (TestCase tc : testCases) {
            TestResultDTO resultDTO = runTestCase(tc.getId());
            results.add(resultDTO);
            if (resultDTO.getStatus() == TestStatus.PASSED) {
                passed++;
            } else {
                failed++;
            }
        }

        TestReportDTO report = new TestReportDTO();
        report.setTemplateId(templateId);
        report.setTotalCount(testCases.size());
        report.setPassedCount(passed);
        report.setFailedCount(failed);
        report.setResults(results);
        report.setExecutedAt(Instant.now());
        return report;
    }

    // ── Import / Export ──

    @Transactional(readOnly = true)
    public String exportTestCases(Long templateId) {
        List<TestCase> testCases = testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
        List<TestCaseDTO> dtos = testCases.stream().map(this::toTestCaseDTO).toList();
        try {
            return objectMapper.writeValueAsString(dtos);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.TEST_CASE_EXECUTION_FAILED,
                    "Failed to export test cases: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public List<TestCaseDTO> importTestCases(Long templateId, String json) {
        try {
            List<CreateTestCaseRequest> requests = objectMapper.readValue(json,
                    new TypeReference<List<CreateTestCaseRequest>>() {});
            List<TestCaseDTO> imported = new ArrayList<>();
            for (CreateTestCaseRequest req : requests) {
                imported.add(createTestCase(templateId, req));
            }
            return imported;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.TEST_CASE_EXECUTION_FAILED,
                    "Failed to import test cases: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // ── Comparison logic ──

    ComparisonResult compare(ComparisonType type, Map<String, Object> actual, Map<String, Object> expected) {
        return switch (type) {
            case VARIABLE_VALUE -> compareVariableValues(actual, expected);
            case TEXT_CONTENT -> compareTextContent(actual, expected);
            case FILE_SNAPSHOT -> compareFileSnapshot(actual, expected);
        };
    }

    private ComparisonResult compareVariableValues(Map<String, Object> actual, Map<String, Object> expected) {
        if (expected == null || expected.isEmpty()) {
            return new ComparisonResult(true, null);
        }
        List<String> diffs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object actualVal = actual.get(entry.getKey());
            Object expectedVal = entry.getValue();
            if (!Objects.equals(String.valueOf(actualVal), String.valueOf(expectedVal))) {
                diffs.add("Variable '" + entry.getKey() + "': expected='" + expectedVal + "', actual='" + actualVal + "'");
            }
        }
        if (diffs.isEmpty()) {
            return new ComparisonResult(true, null);
        }
        return new ComparisonResult(false, String.join("; ", diffs));
    }

    private ComparisonResult compareTextContent(Map<String, Object> actual, Map<String, Object> expected) {
        String actualText = actual.getOrDefault("_textContent", "").toString();
        String expectedText = expected.getOrDefault("_textContent", "").toString();
        if (actualText.equals(expectedText)) {
            return new ComparisonResult(true, null);
        }
        return new ComparisonResult(false, "Text content mismatch: expected length=" + expectedText.length()
                + ", actual length=" + actualText.length());
    }

    private ComparisonResult compareFileSnapshot(Map<String, Object> actual, Map<String, Object> expected) {
        String actualHash = computeHash(actual.getOrDefault("_fileContent", "").toString());
        String expectedHash = expected.getOrDefault("_snapshotHash", "").toString();
        if (actualHash.equals(expectedHash)) {
            return new ComparisonResult(true, null);
        }
        return new ComparisonResult(false, "File snapshot mismatch: expected hash=" + expectedHash
                + ", actual hash=" + actualHash);
    }

    String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    // ── Private helpers ──

    private TestCase findTestCaseOrThrow(Long testCaseId) {
        return testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEST_CASE_NOT_FOUND,
                        "Test case not found: " + testCaseId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, Map.class);
    }

    private TestCaseDTO toTestCaseDTO(TestCase entity) {
        TestCaseDTO dto = new TestCaseDTO();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setName(entity.getName());
        dto.setTestDataJson(entity.getTestDataJson());
        dto.setExpectedResultJson(entity.getExpectedResultJson());
        dto.setComparisonType(entity.getComparisonType());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private TestResultDTO toTestResultDTO(TestResult entity) {
        TestResultDTO dto = new TestResultDTO();
        dto.setId(entity.getId());
        dto.setTestCaseId(entity.getTestCaseId());
        dto.setStatus(entity.getStatus());
        dto.setActualResultJson(entity.getActualResultJson());
        dto.setDiffDetails(entity.getDiffDetails());
        dto.setExecutedAt(entity.getExecutedAt());
        return dto;
    }

    record ComparisonResult(boolean passed, String diffDetails) {}
}
