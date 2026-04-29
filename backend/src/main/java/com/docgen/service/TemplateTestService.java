package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.*;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TestCaseRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing template test cases and executing tests.
 * Execution runs the real generation pipeline and Docxtemplater render, then applies the selected comparison strategy.
 */
@Service
public class TemplateTestService {

    private static final Logger log = LoggerFactory.getLogger(TemplateTestService.class);
    private static final int MAX_ACTUAL_JSON_CHARS = 50_000;
    private static final int MAX_DOCX_EXTRACT_BYTES = 512_000;
    /** Maximum page size for listing test cases (abuse prevention). */
    private static final int MAX_TEST_CASE_PAGE_SIZE = 500;
    /** Maximum page size for listing trial (test) results per scenario. */
    private static final int MAX_TEST_RESULT_PAGE_SIZE = 100;

    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final ObjectMapper objectMapper;
    private final DocumentGeneratorService documentGeneratorService;
    private final DocxTextExtractor docxTextExtractor;
    private final TemplateRepository templateRepository;
    private final DocumentStorageService documentStorageService;

    public TemplateTestService(TestCaseRepository testCaseRepository,
                               TestResultRepository testResultRepository,
                               ObjectMapper objectMapper,
                               DocumentGeneratorService documentGeneratorService,
                               DocxTextExtractor docxTextExtractor,
                               TemplateRepository templateRepository,
                               DocumentStorageService documentStorageService) {
        this.testCaseRepository = testCaseRepository;
        this.testResultRepository = testResultRepository;
        this.objectMapper = objectMapper;
        this.documentGeneratorService = documentGeneratorService;
        this.docxTextExtractor = docxTextExtractor;
        this.templateRepository = templateRepository;
        this.documentStorageService = documentStorageService;
    }


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
        return toTestCaseDTO(testCase, null);
    }

    @Transactional(readOnly = true)
    public Page<TestCaseDTO> listTestCases(Long templateId, String nameQuery, Pageable pageable) {
        Pageable safePageable = capPageable(pageable);
        Page<TestCase> page = StringUtils.hasText(nameQuery)
                ? testCaseRepository.findByTemplateIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                        templateId, nameQuery.trim(), safePageable)
                : testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId, safePageable);
        List<Long> ids = page.getContent().stream().map(TestCase::getId).toList();
        Map<Long, TestResult> latestByCaseId = loadLatestResultsMap(ids);
        List<TestCaseDTO> content = page.getContent().stream()
                .map(tc -> {
                    TestResult row = latestByCaseId.get(tc.getId());
                    TestResultDTO last = row != null ? toTestResultDTO(row, tc.getName()) : null;
                    return toTestCaseDTO(tc, last);
                })
                .toList();
        return new PageImpl<>(content, safePageable, page.getTotalElements());
    }

    /** Visible for unit tests in the same package. */
    static Pageable capPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();
        if (size < 1) {
            size = 20;
        } else if (size > MAX_TEST_CASE_PAGE_SIZE) {
            size = MAX_TEST_CASE_PAGE_SIZE;
        }
        if (page == pageable.getPageNumber() && size == pageable.getPageSize()) {
            return pageable;
        }
        return PageRequest.of(page, size, pageable.getSort());
    }

    private Map<Long, TestResult> loadLatestResultsMap(List<Long> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TestResult> rows = testResultRepository.findLatestResultsForTestCaseIds(testCaseIds);
        Map<Long, TestResult> map = new HashMap<>(rows.size());
        for (TestResult r : rows) {
            map.put(r.getTestCaseId(), r);
        }
        return map;
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
        return toTestCaseDTO(testCase, null);
    }

    @Transactional
    public void deleteTestCase(Long testCaseId) {
        TestCase testCase = findTestCaseOrThrow(testCaseId);
        testCaseRepository.delete(testCase);
    }

    /**
     * Paged trial run history for a test case, newest first ({@code executedAt DESC, id DESC}).
     */
    @Transactional(readOnly = true)
    public Page<TestResultDTO> listTestResults(Long testCaseId, Pageable pageable) {
        TestCase testCase = findTestCaseOrThrow(testCaseId);
        Pageable safe = capTestResultPageable(pageable);
        Page<TestResult> page = testResultRepository.pageByTestCaseId(testCaseId, safe);
        return page.map(r -> toTestResultDTO(r, testCase.getName()));
    }

    /** Visible for unit tests. */
    static Pageable capTestResultPageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();
        if (size < 1) {
            size = 20;
        } else if (size > MAX_TEST_RESULT_PAGE_SIZE) {
            size = MAX_TEST_RESULT_PAGE_SIZE;
        }
        Sort sort = Sort.by(Sort.Order.desc("executedAt"), Sort.Order.desc("id"));
        return PageRequest.of(page, size, sort);
    }


    @Transactional
    public TestResultDTO runTestCase(Long testCaseId) {
        TestCase testCase = findTestCaseOrThrow(testCaseId);
        try {
            Map<String, Object> parameters = parseJson(testCase.getTestDataJson());
            Map<String, Object> expectedResult = testCase.getExpectedResultJson() != null
                    ? parseJson(testCase.getExpectedResultJson()) : Collections.emptyMap();

            TemplateTestRenderOutcome render = documentGeneratorService.renderForTemplateTest(
                    testCase.getTemplateId(), parameters);

            ComparisonResult comparison = runComparison(testCase.getComparisonType(), render, expectedResult);

            Template template = templateRepository.findById(testCase.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.TEMPLATE_NOT_FOUND,
                            "Template not found: " + testCase.getTemplateId()));

            byte[] docx = render.docxBytes();
            boolean hasRenderableOutput = docx != null && docx.length > 0;
            Long sampleDocumentId = null;
            if (hasRenderableOutput) {
                try {
                    GenerateDocumentResponse stored =
                            documentStorageService.store(template, docx, "DOCX", "TEMP");
                    if (stored.getDocumentId() != null) {
                        sampleDocumentId = stored.getDocumentId();
                    }
                } catch (Exception e) {
                    log.warn("Trial sample document storage failed testCaseId={}: {}", testCaseId, e.getMessage());
                }
            }

            String diff = comparison.diffDetails();
            TestStatus finalStatus;
            boolean storageRequired = hasRenderableOutput;
            boolean storageOk = sampleDocumentId != null;
            if (storageRequired && !storageOk) {
                finalStatus = TestStatus.FAILED;
                String storageMsg = "Sample document could not be registered for download.";
                diff = (diff != null && !diff.isBlank()) ? (diff + "\n\n" + storageMsg) : storageMsg;
            } else {
                finalStatus = comparison.passed() ? TestStatus.PASSED : TestStatus.FAILED;
            }

            TestResult result = new TestResult();
            result.setTestCaseId(testCaseId);
            result.setStatus(finalStatus);
            result.setActualResultJson(buildActualResultJson(testCase.getComparisonType(), render));
            result.setDiffDetails(diff);
            result.setExecutedAt(Instant.now());
            result.setGeneratedDocumentId(sampleDocumentId);
            result = testResultRepository.save(result);
            return toTestResultDTO(result, testCase.getName());
        } catch (BusinessException e) {
            log.warn("Test case execution failed (business) testCaseId={}: {}", testCaseId, e.getMessage());
            return persistFailure(testCaseId, testCase.getName(), "Execution failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Test case execution failed for testCaseId={}: {}", testCaseId, e.getMessage());
            return persistFailure(testCaseId, testCase.getName(), "Execution error: " + e.getMessage());
        }
    }

    private TestResultDTO persistFailure(Long testCaseId, String testCaseName, String diffDetails) {
        TestResult result = new TestResult();
        result.setTestCaseId(testCaseId);
        result.setStatus(TestStatus.FAILED);
        result.setDiffDetails(diffDetails);
        result.setExecutedAt(Instant.now());
        result = testResultRepository.save(result);
        return toTestResultDTO(result, testCaseName);
    }

    private ComparisonResult runComparison(ComparisonType type,
                                           TemplateTestRenderOutcome render,
                                           Map<String, Object> expected) {
        return switch (type) {
            case VARIABLE_VALUE -> compareVariableValues(render.dataContext(), expected);
            case TEXT_CONTENT -> {
                byte[] bytes = render.docxBytes() != null ? render.docxBytes() : new byte[0];
                ExtractedText extracted = docxTextExtractor.extractText(new ByteArrayInputStream(bytes),
                        MAX_DOCX_EXTRACT_BYTES);
                yield compareTextContent(extracted.text(), expected);
            }
            case FILE_SNAPSHOT -> compareFileSnapshot(render.docxBytes(), expected);
        };
    }

    private String buildActualResultJson(ComparisonType type,
                                         TemplateTestRenderOutcome render) throws JsonProcessingException {
        return switch (type) {
            case VARIABLE_VALUE -> truncateJson(objectMapper.writeValueAsString(render.dataContext()));
            case TEXT_CONTENT -> {
                byte[] bytes = render.docxBytes() != null ? render.docxBytes() : new byte[0];
                ExtractedText extracted = docxTextExtractor.extractText(new ByteArrayInputStream(bytes),
                        MAX_DOCX_EXTRACT_BYTES);
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("extractedTextLength", extracted.text() != null ? extracted.text().length() : 0);
                summary.put("truncated", extracted.truncated());
                summary.put("docxBytesLength", bytes.length);
                yield objectMapper.writeValueAsString(summary);
            }
            case FILE_SNAPSHOT -> {
                byte[] bytes = render.docxBytes() != null ? render.docxBytes() : new byte[0];
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("docxBytesLength", bytes.length);
                summary.put("docxSha256", bytes.length == 0 ? "" : computeHashBytes(bytes));
                yield objectMapper.writeValueAsString(summary);
            }
        };
    }

    private String truncateJson(String json) throws JsonProcessingException {
        if (json == null) {
            return "{}";
        }
        if (json.length() <= MAX_ACTUAL_JSON_CHARS) {
            return json;
        }
        Map<String, Object> truncated = new LinkedHashMap<>();
        truncated.put("_truncated", true);
        truncated.put("_jsonPrefix", json.substring(0, MAX_ACTUAL_JSON_CHARS));
        truncated.put("_originalLength", json.length());
        return objectMapper.writeValueAsString(truncated);
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


    @Transactional(readOnly = true)
    public String exportTestCases(Long templateId) {
        List<TestCase> testCases = testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
        List<TestCaseDTO> dtos = testCases.stream().map(tc -> toTestCaseDTO(tc, null)).toList();
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


    /**
     * Package-visible for unit tests; only VARIABLE_VALUE is supported through this entry point.
     */
    ComparisonResult compare(ComparisonType type, Map<String, Object> actual, Map<String, Object> expected) {
        if (type != ComparisonType.VARIABLE_VALUE) {
            throw new IllegalArgumentException("compare() supports VARIABLE_VALUE only");
        }
        return compareVariableValues(actual, expected);
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

    private ComparisonResult compareTextContent(String extractedText, Map<String, Object> expected) {
        String expectedText = expected.getOrDefault("_textContent", "").toString();
        String a = normalizeNewlines(extractedText != null ? extractedText : "");
        String e = normalizeNewlines(expectedText);
        if (a.equals(e)) {
            return new ComparisonResult(true, null);
        }
        return new ComparisonResult(false, "Text content mismatch: expected length=" + e.length()
                + ", actual length=" + a.length());
    }

    private static String normalizeNewlines(String s) {
        return s.replace("\r\n", "\n").trim();
    }

    private ComparisonResult compareFileSnapshot(byte[] docxBytes, Map<String, Object> expected) {
        if (docxBytes == null || docxBytes.length == 0) {
            return new ComparisonResult(false, "Empty document output");
        }
        String actualHash = computeHashBytes(docxBytes);
        String expectedHash = expected.getOrDefault("_snapshotHash", "").toString();
        if (actualHash.equalsIgnoreCase(expectedHash)) {
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

    public String computeHashBytes(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }


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

    private TestCaseDTO toTestCaseDTO(TestCase entity, TestResultDTO lastRun) {
        TestCaseDTO dto = new TestCaseDTO();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setName(entity.getName());
        dto.setTestDataJson(entity.getTestDataJson());
        dto.setExpectedResultJson(entity.getExpectedResultJson());
        dto.setComparisonType(entity.getComparisonType());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setLastRun(lastRun);
        return dto;
    }

    private TestResultDTO toTestResultDTO(TestResult entity, String testCaseName) {
        TestResultDTO dto = new TestResultDTO();
        dto.setId(entity.getId());
        dto.setTestCaseId(entity.getTestCaseId());
        dto.setTestCaseName(testCaseName);
        dto.setStatus(entity.getStatus());
        dto.setActualResultJson(entity.getActualResultJson());
        dto.setDiffDetails(entity.getDiffDetails());
        dto.setExecutedAt(entity.getExecutedAt());
        if (entity.getGeneratedDocumentId() != null) {
            dto.setSampleDocumentId(entity.getGeneratedDocumentId());
            dto.setSampleDocumentDownloadUrl(
                    String.format("/api/documents/%d/download", entity.getGeneratedDocumentId()));
        }
        return dto;
    }

    record ComparisonResult(boolean passed, String diffDetails) {}
}

