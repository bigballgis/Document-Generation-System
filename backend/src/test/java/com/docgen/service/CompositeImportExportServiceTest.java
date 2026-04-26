package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.CompositeCoverageReport;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.ComparisonType;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.exception.BusinessException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeImportExportServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private AssemblyConfigService assemblyConfigService;
    @Mock private MinioClient minioClient;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private CompositeCoverageService compositeCoverageService;
    @Mock private ParameterService parameterService;
    @Mock private com.docgen.repository.ParameterRepository parameterRepository;

    private CompositeImportExportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        service = new CompositeImportExportService(
                templateRepository, assemblyConfigService,
                minioClient, objectMapper,
                testCaseRepository, compositeCoverageService,
                parameterService, parameterRepository);
        Field bucketField = CompositeImportExportService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen-test");
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── exportAsZip: ZIP contains all required files ──

    @Test
    void exportAsZip_containsAllRequiredFiles() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        AssemblyConfigDTO config = new AssemblyConfigDTO();
        AssemblySegmentEntry segEntry = new AssemblySegmentEntry();
        segEntry.setFilePath("segments/1/intro.docx");
        segEntry.setName("intro");
        segEntry.setSegmentType("CHAPTER");
        segEntry.setPosition(0);
        segEntry.setEnabled(true);
        config.setSegments(List.of(segEntry));
        when(assemblyConfigService.deserialize(any())).thenReturn(config);

        mockMinioDownload("segments/1/intro.docx", new byte[]{0x50, 0x4B, 0x03, 0x04});

        TestCase tc = createTestCase(300L, "TC1", "{\"a\":1}", "{\"result\":2}", ComparisonType.VARIABLE_VALUE);
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tc));

        CompositeCoverageReport report = new CompositeCoverageReport();
        report.setOverallCoveragePercent(85.0);
        report.setSegmentCoverages(List.of());
        when(compositeCoverageService.checkCoverage(1L)).thenReturn(report);

        byte[] zipBytes = service.exportAsZip(1L);

        Map<String, byte[]> entries = extractZipEntries(zipBytes);
        assertTrue(entries.containsKey("config.json"), "ZIP must contain config.json");
        assertTrue(entries.containsKey("segments/intro.docx"), "ZIP must contain segment docx");
        assertTrue(entries.containsKey("test-data.json"), "ZIP must contain test-data.json");
        assertTrue(entries.containsKey("coverage-report.json"), "ZIP must contain coverage-report.json");
    }

    // ── exportAsZip: empty arrays when no data ──

    @Test
    void exportAsZip_emptyArraysWhenNoData() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();

        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        mockCoverageSuccess();

        byte[] zipBytes = service.exportAsZip(1L);

        List<Map<String, Object>> td = parseJsonFromZip(zipBytes, "test-data.json");
        assertTrue(td.isEmpty());
    }

    // ── exportAsZip: coverage failure → ZIP still succeeds ──

    @Test
    void exportAsZip_coverageFailure_zipStillSucceeds() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();

        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(compositeCoverageService.checkCoverage(1L)).thenThrow(new RuntimeException("Coverage service down"));

        byte[] zipBytes = service.exportAsZip(1L);

        Map<String, byte[]> entries = extractZipEntries(zipBytes);
        assertTrue(entries.containsKey("config.json"));
        assertFalse(entries.containsKey("coverage-report.json"));
    }

    // ── importFromZip: with test data ──

    @Test
    void importFromZip_withTestData_createsRecords() throws Exception {
        byte[] zipBytes = buildImportZip(true);
        MockMultipartFile file = new MockMultipartFile("file", "import.zip", "application/zip", zipBytes);

        Template saved = createCompositeTemplate(99L, "Imported");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateDTO result = service.importFromZip(file, 1L);

        assertNotNull(result);
        verify(testCaseRepository, times(1)).save(any(TestCase.class));
    }

    // ── importFromZip: minimal ZIP ──

    @Test
    void importFromZip_minimalZip_noError() throws Exception {
        byte[] zipBytes = buildImportZip(false);
        MockMultipartFile file = new MockMultipartFile("file", "import.zip", "application/zip", zipBytes);

        Template saved = createCompositeTemplate(99L, "Imported");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        TemplateDTO result = service.importFromZip(file, 1L);

        assertNotNull(result);
        verify(testCaseRepository, never()).save(any(TestCase.class));
    }

    // ── Helper methods ──

    private Template createCompositeTemplate(Long id, String name) {
        Template template = new Template();
        template.setId(id);
        template.setTenantId(1L);
        template.setName(name);
        template.setDescription("Test composite template");
        template.setTemplateFilePath("composite://" + name);
        template.setTemplateType("COMPOSITE");
        template.setAssemblyConfig("{\"segments\":[]}");
        template.setCreatedBy(1L);
        template.setStatus("ACTIVE");
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }

    private TestCase createTestCase(Long id, String name, String testData, String expected, ComparisonType type) {
        TestCase tc = new TestCase();
        tc.setId(id);
        tc.setTemplateId(1L);
        tc.setName(name);
        tc.setTestDataJson(testData);
        tc.setExpectedResultJson(expected);
        tc.setComparisonType(type);
        return tc;
    }

    private void mockEmptyAssemblyConfig() {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(List.of());
        when(assemblyConfigService.deserialize(any())).thenReturn(config);
    }

    private void mockCoverageSuccess() {
        CompositeCoverageReport report = new CompositeCoverageReport();
        report.setOverallCoveragePercent(100.0);
        report.setSegmentCoverages(List.of());
        when(compositeCoverageService.checkCoverage(anyLong())).thenReturn(report);
    }

    private void mockMinioDownload(String path, byte[] content) throws Exception {
        GetObjectResponse mockResponse = new GetObjectResponse(
                Headers.of(), "docgen-test", "", "",
                new ByteArrayInputStream(content));
        when(minioClient.getObject(any())).thenReturn(mockResponse);
    }

    private Map<String, byte[]> extractZipEntries(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    private List<Map<String, Object>> parseJsonFromZip(byte[] zipBytes, String fileName) throws Exception {
        Map<String, byte[]> entries = extractZipEntries(zipBytes);
        byte[] content = entries.get(fileName);
        assertNotNull(content, fileName + " not found in ZIP");
        return objectMapper.readValue(content, new TypeReference<List<Map<String, Object>>>() {});
    }

    private byte[] buildImportZip(boolean includeTestData) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            CompositeImportExportService.CompositeExportConfig exportConfig =
                    new CompositeImportExportService.CompositeExportConfig();
            exportConfig.setTemplateName("Imported");
            exportConfig.setTemplateDescription("Test import");
            CompositeImportExportService.CompositeExportConfig.SegmentExportEntry seg =
                    new CompositeImportExportService.CompositeExportConfig.SegmentExportEntry();
            seg.setSegmentName("intro");
            seg.setPosition(0);
            seg.setEnabled(true);
            exportConfig.setSegments(List.of(seg));

            byte[] configBytes = objectMapper.writeValueAsBytes(exportConfig);
            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write(configBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("segments/intro.docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();

            if (includeTestData) {
                List<Map<String, Object>> testList = List.of(Map.of(
                        "name", "TC1", "testDataJson", "{\"a\":1}",
                        "expectedResultJson", "{\"r\":2}", "comparisonType", "VARIABLE_VALUE"));
                zos.putNextEntry(new ZipEntry("test-data.json"));
                zos.write(objectMapper.writeValueAsBytes(testList));
                zos.closeEntry();
            }

            zos.finish();
        }
        return baos.toByteArray();
    }
}
