package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.*;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TestCaseRepository;
import com.docgen.repository.TestResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateTestServiceTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private DocumentGeneratorService documentGeneratorService;

    @Mock
    private DocxTextExtractor docxTextExtractor;

    private TemplateTestService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new TemplateTestService(testCaseRepository, testResultRepository, objectMapper,
                documentGeneratorService, docxTextExtractor);
        lenient().when(testResultRepository.findFirstByTestCaseIdOrderByExecutedAtDesc(anyLong()))
                .thenReturn(Optional.empty());
    }

    // ── createTestCase ──

    @Test
    void createTestCase_success() {
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> {
            TestCase tc = inv.getArgument(0);
            tc.setId(1L);
            tc.setCreatedAt(Instant.now());
            tc.setUpdatedAt(Instant.now());
            return tc;
        });

        CreateTestCaseRequest request = new CreateTestCaseRequest();
        request.setName("Test 1");
        request.setTestDataJson("{\"name\":\"John\"}");
        request.setExpectedResultJson("{\"name\":\"John\"}");
        request.setComparisonType(ComparisonType.VARIABLE_VALUE);

        TestCaseDTO result = service.createTestCase(100L, request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(100L, result.getTemplateId());
        assertEquals("Test 1", result.getName());
        assertEquals(ComparisonType.VARIABLE_VALUE, result.getComparisonType());
    }

    @Test
    void createTestCase_defaultComparisonType() {
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> {
            TestCase tc = inv.getArgument(0);
            tc.setId(2L);
            tc.setCreatedAt(Instant.now());
            tc.setUpdatedAt(Instant.now());
            return tc;
        });

        CreateTestCaseRequest request = new CreateTestCaseRequest();
        request.setName("Test default");
        request.setTestDataJson("{\"x\":1}");

        TestCaseDTO result = service.createTestCase(100L, request);
        assertEquals(ComparisonType.VARIABLE_VALUE, result.getComparisonType());
    }

    // ── listTestCases ──

    @Test
    void listTestCases_returnsAll() {
        TestCase tc1 = createSampleTestCase(1L, 100L, "TC1");
        TestCase tc2 = createSampleTestCase(2L, 100L, "TC2");
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(tc1, tc2));

        List<TestCaseDTO> result = service.listTestCases(100L);
        assertEquals(2, result.size());
        assertEquals("TC1", result.get(0).getName());
    }

    // ── updateTestCase ──

    @Test
    void updateTestCase_success() {
        TestCase existing = createSampleTestCase(1L, 100L, "Old Name");
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTestCaseRequest request = new CreateTestCaseRequest();
        request.setName("New Name");
        request.setComparisonType(ComparisonType.TEXT_CONTENT);

        TestCaseDTO result = service.updateTestCase(1L, request);
        assertEquals("New Name", result.getName());
        assertEquals(ComparisonType.TEXT_CONTENT, result.getComparisonType());
    }

    @Test
    void updateTestCase_notFound() {
        when(testCaseRepository.findById(999L)).thenReturn(Optional.empty());

        CreateTestCaseRequest request = new CreateTestCaseRequest();
        request.setName("X");

        assertThrows(ResourceNotFoundException.class, () -> service.updateTestCase(999L, request));
    }

    // ── deleteTestCase ──

    @Test
    void deleteTestCase_success() {
        TestCase existing = createSampleTestCase(1L, 100L, "TC");
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.deleteTestCase(1L);
        verify(testCaseRepository).delete(existing);
    }

    @Test
    void deleteTestCase_notFound() {
        when(testCaseRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteTestCase(999L));
    }

    // ── runTestCase ──

    @Test
    void runTestCase_variableValue_passed() {
        TestCase tc = createSampleTestCase(1L, 100L, "TC");
        tc.setTestDataJson("{\"name\":\"John\",\"age\":\"30\"}");
        tc.setExpectedResultJson("{\"name\":\"John\",\"age\":\"30\"}");
        tc.setComparisonType(ComparisonType.VARIABLE_VALUE);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc));
        when(documentGeneratorService.renderForTemplateTest(eq(100L), any()))
                .thenReturn(new TemplateTestRenderOutcome(Map.of("name", "John", "age", "30"), new byte[]{1, 2, 3}));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });

        TestResultDTO result = service.runTestCase(1L);
        assertEquals(TestStatus.PASSED, result.getStatus());
        assertEquals("TC", result.getTestCaseName());
        assertNull(result.getDiffDetails());
    }

    @Test
    void runTestCase_variableValue_failed() {
        TestCase tc = createSampleTestCase(1L, 100L, "TC");
        tc.setTestDataJson("{\"name\":\"John\"}");
        tc.setExpectedResultJson("{\"name\":\"Jane\"}");
        tc.setComparisonType(ComparisonType.VARIABLE_VALUE);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc));
        when(documentGeneratorService.renderForTemplateTest(eq(100L), any()))
                .thenReturn(new TemplateTestRenderOutcome(Map.of("name", "John"), new byte[]{1}));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            r.setId(11L);
            return r;
        });

        TestResultDTO result = service.runTestCase(1L);
        assertEquals(TestStatus.FAILED, result.getStatus());
        assertNotNull(result.getDiffDetails());
        assertTrue(result.getDiffDetails().contains("name"));
    }

    @Test
    void runTestCase_textContent_passed() {
        TestCase tc = createSampleTestCase(1L, 100L, "TC");
        tc.setTestDataJson("{}");
        tc.setExpectedResultJson("{\"_textContent\":\"Hello World\"}");
        tc.setComparisonType(ComparisonType.TEXT_CONTENT);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc));
        when(documentGeneratorService.renderForTemplateTest(eq(100L), any()))
                .thenReturn(new TemplateTestRenderOutcome(Map.of(), new byte[]{9}));
        when(docxTextExtractor.extractText(any(), anyInt()))
                .thenReturn(new ExtractedText("Hello World", false));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            r.setId(12L);
            return r;
        });

        TestResultDTO result = service.runTestCase(1L);
        assertEquals(TestStatus.PASSED, result.getStatus());
    }

    @Test
    void runTestCase_textContent_failed() {
        TestCase tc = createSampleTestCase(1L, 100L, "TC");
        tc.setTestDataJson("{}");
        tc.setExpectedResultJson("{\"_textContent\":\"World\"}");
        tc.setComparisonType(ComparisonType.TEXT_CONTENT);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc));
        when(documentGeneratorService.renderForTemplateTest(eq(100L), any()))
                .thenReturn(new TemplateTestRenderOutcome(Map.of(), new byte[]{9}));
        when(docxTextExtractor.extractText(any(), anyInt()))
                .thenReturn(new ExtractedText("Hello", false));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            r.setId(13L);
            return r;
        });

        TestResultDTO result = service.runTestCase(1L);
        assertEquals(TestStatus.FAILED, result.getStatus());
        assertTrue(result.getDiffDetails().contains("Text content mismatch"));
    }

    @Test
    void runTestCase_fileSnapshot_passed() {
        byte[] bytes = "file content".getBytes(StandardCharsets.UTF_8);
        String hash = service.computeHashBytes(bytes);
        TestCase tc = createSampleTestCase(1L, 100L, "TC");
        tc.setTestDataJson("{}");
        tc.setExpectedResultJson("{\"_snapshotHash\":\"" + hash + "\"}");
        tc.setComparisonType(ComparisonType.FILE_SNAPSHOT);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc));
        when(documentGeneratorService.renderForTemplateTest(eq(100L), any()))
                .thenReturn(new TemplateTestRenderOutcome(Map.of(), bytes));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            r.setId(14L);
            return r;
        });

        TestResultDTO result = service.runTestCase(1L);
        assertEquals(TestStatus.PASSED, result.getStatus());
    }

    @Test
    void runTestCase_fileSnapshot_failed() {
        TestCase tc = createSampleTestCase(1L, 100L, "TC");
        tc.setTestDataJson("{}");
        tc.setExpectedResultJson("{\"_snapshotHash\":\"wrong_hash\"}");
        tc.setComparisonType(ComparisonType.FILE_SNAPSHOT);
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc));
        when(documentGeneratorService.renderForTemplateTest(eq(100L), any()))
                .thenReturn(new TemplateTestRenderOutcome(Map.of(), "abc".getBytes(StandardCharsets.UTF_8)));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            r.setId(15L);
            return r;
        });

        TestResultDTO result = service.runTestCase(1L);
        assertEquals(TestStatus.FAILED, result.getStatus());
        assertTrue(result.getDiffDetails().contains("File snapshot mismatch"));
    }

    @Test
    void runTestCase_notFound() {
        when(testCaseRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.runTestCase(999L));
    }

    // ── runAllTests ──

    @Test
    void runAllTests_mixedResults() {
        TestCase tc1 = createSampleTestCase(1L, 100L, "TC1");
        tc1.setTestDataJson("{\"name\":\"John\"}");
        tc1.setExpectedResultJson("{\"name\":\"John\"}");
        tc1.setComparisonType(ComparisonType.VARIABLE_VALUE);

        TestCase tc2 = createSampleTestCase(2L, 100L, "TC2");
        tc2.setTestDataJson("{\"name\":\"John\"}");
        tc2.setExpectedResultJson("{\"name\":\"Jane\"}");
        tc2.setComparisonType(ComparisonType.VARIABLE_VALUE);

        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(tc1, tc2));
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc1));
        when(testCaseRepository.findById(2L)).thenReturn(Optional.of(tc2));
        when(documentGeneratorService.renderForTemplateTest(eq(100L), argThat(m -> true)))
                .thenAnswer(inv -> {
                    Map<String, Object> p = inv.getArgument(1);
                    if (p.containsKey("name") && "John".equals(String.valueOf(p.get("name")))) {
                        return new TemplateTestRenderOutcome(Map.of("name", "John"), new byte[]{1});
                    }
                    return new TemplateTestRenderOutcome(Map.of(), new byte[]{1});
                });
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(inv -> {
            TestResult r = inv.getArgument(0);
            r.setId(r.getTestCaseId() * 10);
            return r;
        });

        TestReportDTO report = service.runAllTests(100L);
        assertEquals(100L, report.getTemplateId());
        assertEquals(2, report.getTotalCount());
        assertEquals(1, report.getPassedCount());
        assertEquals(1, report.getFailedCount());
        assertEquals(2, report.getResults().size());
    }

    @Test
    void runAllTests_emptyTestCases() {
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of());

        TestReportDTO report = service.runAllTests(100L);
        assertEquals(0, report.getTotalCount());
        assertEquals(0, report.getPassedCount());
        assertEquals(0, report.getFailedCount());
    }

    // ── Import / Export ──

    @Test
    void exportTestCases_success() {
        TestCase tc = createSampleTestCase(1L, 100L, "TC1");
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(tc));

        String json = service.exportTestCases(100L);
        assertNotNull(json);
        assertTrue(json.contains("TC1"));
    }

    @Test
    void importTestCases_success() {
        String json = "[{\"name\":\"Imported\",\"testDataJson\":\"{}\",\"comparisonType\":\"VARIABLE_VALUE\"}]";
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> {
            TestCase tc = inv.getArgument(0);
            tc.setId(5L);
            tc.setCreatedAt(Instant.now());
            tc.setUpdatedAt(Instant.now());
            return tc;
        });

        List<TestCaseDTO> imported = service.importTestCases(100L, json);
        assertEquals(1, imported.size());
        assertEquals("Imported", imported.get(0).getName());
    }

    // ── Comparison logic ──

    @Test
    void compare_variableValue_emptyExpected() {
        var result = service.compare(ComparisonType.VARIABLE_VALUE,
                java.util.Map.of("a", "1"), java.util.Map.of());
        assertTrue(result.passed());
    }

    @Test
    void computeHash_deterministic() {
        String h1 = service.computeHash("test");
        String h2 = service.computeHash("test");
        assertEquals(h1, h2);
        assertNotEquals(h1, service.computeHash("other"));
    }

    @Test
    void computeHashBytes_deterministic() {
        byte[] b = "x".getBytes(StandardCharsets.UTF_8);
        assertEquals(service.computeHashBytes(b), service.computeHashBytes(b));
    }

    // ── Helpers ──

    private TestCase createSampleTestCase(Long id, Long templateId, String name) {
        TestCase tc = new TestCase();
        tc.setId(id);
        tc.setTemplateId(templateId);
        tc.setName(name);
        tc.setTestDataJson("{}");
        tc.setExpectedResultJson("{}");
        tc.setComparisonType(ComparisonType.VARIABLE_VALUE);
        tc.setCreatedAt(Instant.now());
        tc.setUpdatedAt(Instant.now());
        return tc;
    }
}
