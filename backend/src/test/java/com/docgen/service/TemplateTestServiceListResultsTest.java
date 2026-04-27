package com.docgen.service;

import com.docgen.dto.TestResultDTO;
import com.docgen.entity.TestCase;
import com.docgen.entity.TestResult;
import com.docgen.entity.TestStatus;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TestCaseRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for paged trial result listing ({@link TemplateTestService#listTestResults}).
 */
@ExtendWith(MockitoExtension.class)
class TemplateTestServiceListResultsTest {

    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private TestResultRepository testResultRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private DocumentGeneratorService documentGeneratorService;
    @Mock
    private DocxTextExtractor docxTextExtractor;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private DocumentStorageService documentStorageService;

    private TemplateTestService service;

    @BeforeEach
    void setUp() {
        service = new TemplateTestService(
                testCaseRepository,
                testResultRepository,
                objectMapper,
                documentGeneratorService,
                docxTextExtractor,
                templateRepository,
                documentStorageService);
    }

    @Test
    void capTestResultPageable_clampsSizeAndUsesFixedSort() {
        Pageable raw = PageRequest.of(2, 999);
        Pageable safe = TemplateTestService.capTestResultPageable(raw);
        assertEquals(2, safe.getPageNumber());
        assertEquals(100, safe.getPageSize());
        assertTrue(safe.getSort().getOrderFor("executedAt").isDescending());
        assertTrue(safe.getSort().getOrderFor("id").isDescending());
    }

    @Test
    void listTestResults_throwsWhenCaseMissing() {
        when(testCaseRepository.findById(42L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.listTestResults(42L, PageRequest.of(0, 10)));
        verifyNoInteractions(testResultRepository);
    }

    @Test
    void listTestResults_returnsMappedPage() {
        TestCase tc = new TestCase();
        tc.setId(1L);
        tc.setName("Scenario A");
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(tc));

        TestResult r = new TestResult();
        r.setId(10L);
        r.setTestCaseId(1L);
        r.setStatus(TestStatus.PASSED);
        r.setDiffDetails("ok");
        r.setExecutedAt(Instant.parse("2026-04-27T12:00:00Z"));

        Pageable expectedPageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("executedAt"), Sort.Order.desc("id")));
        Page<TestResult> pg = new PageImpl<>(List.of(r), expectedPageable, 1);
        when(testResultRepository.pageByTestCaseId(eq(1L), any(Pageable.class))).thenReturn(pg);

        Page<TestResultDTO> out = service.listTestResults(1L, PageRequest.of(0, 20));

        assertEquals(1, out.getTotalElements());
        assertEquals(1, out.getContent().size());
        assertEquals("Scenario A", out.getContent().get(0).getTestCaseName());
        assertEquals(TestStatus.PASSED, out.getContent().get(0).getStatus());
        verify(testResultRepository).pageByTestCaseId(eq(1L), argThat(p ->
                p.getPageSize() == 20
                        && p.getSort().getOrderFor("executedAt").isDescending()));
    }
}
