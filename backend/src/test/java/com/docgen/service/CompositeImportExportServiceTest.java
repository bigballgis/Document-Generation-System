package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.CompositeCoverageReport;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.ComparisonType;
import com.docgen.entity.DataSource;
import com.docgen.entity.Expression;
import com.docgen.entity.Segment;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.exception.BusinessException;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.SegmentRepository;
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
    @Mock private SegmentRepository segmentRepository;
    @Mock private AssemblyConfigService assemblyConfigService;
    @Mock private MinioClient minioClient;
    @Mock private DataSourceRepository dataSourceRepository;
    @Mock private ExpressionRepository expressionRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private CompositeCoverageService compositeCoverageService;

    private CompositeImportExportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        service = new CompositeImportExportService(
                templateRepository, segmentRepository, assemblyConfigService,
                minioClient, objectMapper,
                dataSourceRepository, expressionRepository, testCaseRepository,
                compositeCoverageService);
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
        segEntry.setSegmentId(10L);
        segEntry.setPosition(0);
        segEntry.setEnabled(true);
        config.setSegments(List.of(segEntry));
        when(assemblyConfigService.deserialize(any())).thenReturn(config);

        Segment segment = createSegment(10L, "intro");
        when(segmentRepository.findById(10L)).thenReturn(Optional.of(segment));
        mockMinioDownload("segments/1/intro.docx", new byte[]{0x50, 0x4B, 0x03, 0x04});

        DataSource ds = createDataSource(100L, "dbSource", "DATABASE",
                "{\"host\":\"localhost\",\"password\":\"secret123\"}");
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of(ds));

        Expression expr = createExpression(200L, "calcTotal", "JAVASCRIPT", "a+b", 1);
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of(expr));

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
        assertTrue(entries.containsKey("data-sources.json"), "ZIP must contain data-sources.json");
        assertTrue(entries.containsKey("expressions.json"), "ZIP must contain expressions.json");
        assertTrue(entries.containsKey("test-data.json"), "ZIP must contain test-data.json");
        assertTrue(entries.containsKey("coverage-report.json"), "ZIP must contain coverage-report.json");
    }

    // ── exportAsZip: credential masking for DATABASE password ──

    @Test
    void exportAsZip_masksDbPassword() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();

        DataSource ds = createDataSource(100L, "dbSource", "DATABASE",
                "{\"host\":\"db.example.com\",\"port\":5432,\"password\":\"superSecret\"}");
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of(ds));
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        mockCoverageSuccess();

        byte[] zipBytes = service.exportAsZip(1L);
        List<Map<String, Object>> dataSources = parseJsonFromZip(zipBytes, "data-sources.json");

        assertEquals(1, dataSources.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) dataSources.get(0).get("config");
        assertEquals("__CREDENTIAL_PLACEHOLDER__", config.get("password"));
        assertEquals("db.example.com", config.get("host"));
        assertEquals(5432, config.get("port"));
    }

    // ── exportAsZip: credential masking for apiKey and clientSecret ──

    @Test
    void exportAsZip_masksApiKeyAndClientSecret() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();

        DataSource ds = createDataSource(101L, "apiSource", "HTTP_API",
                "{\"url\":\"https://api.example.com\",\"apiKey\":\"key123\",\"clientSecret\":\"secret456\"}");
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of(ds));
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        mockCoverageSuccess();

        byte[] zipBytes = service.exportAsZip(1L);
        List<Map<String, Object>> dataSources = parseJsonFromZip(zipBytes, "data-sources.json");

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) dataSources.get(0).get("config");
        assertEquals("__CREDENTIAL_PLACEHOLDER__", config.get("apiKey"));
        assertEquals("__CREDENTIAL_PLACEHOLDER__", config.get("clientSecret"));
        assertEquals("https://api.example.com", config.get("url"));
    }

    // ── exportAsZip: non-sensitive fields preserved ──

    @Test
    void exportAsZip_preservesNonSensitiveFields() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();

        DataSource ds = createDataSource(102L, "safeSource", "HTTP_API",
                "{\"url\":\"https://safe.com\",\"timeout\":30}");
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of(ds));
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        mockCoverageSuccess();

        byte[] zipBytes = service.exportAsZip(1L);
        List<Map<String, Object>> dataSources = parseJsonFromZip(zipBytes, "data-sources.json");

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) dataSources.get(0).get("config");
        assertEquals("https://safe.com", config.get("url"));
        assertEquals(30, config.get("timeout"));
    }

    // ── exportAsZip: empty arrays when no data ──

    @Test
    void exportAsZip_emptyArraysWhenNoData() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();

        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of());
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        mockCoverageSuccess();

        byte[] zipBytes = service.exportAsZip(1L);

        List<Map<String, Object>> ds = parseJsonFromZip(zipBytes, "data-sources.json");
        List<Map<String, Object>> expr = parseJsonFromZip(zipBytes, "expressions.json");
        List<Map<String, Object>> td = parseJsonFromZip(zipBytes, "test-data.json");

        assertTrue(ds.isEmpty(), "data-sources.json should be empty array");
        assertTrue(expr.isEmpty(), "expressions.json should be empty array");
        assertTrue(td.isEmpty(), "test-data.json should be empty array");
    }

    // ── exportAsZip: coverage failure → ZIP still succeeds without coverage-report.json ──

    @Test
    void exportAsZip_coverageFailure_zipStillSucceeds() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();

        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of());
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(compositeCoverageService.checkCoverage(1L)).thenThrow(new RuntimeException("Coverage service down"));

        byte[] zipBytes = service.exportAsZip(1L);

        Map<String, byte[]> entries = extractZipEntries(zipBytes);
        assertTrue(entries.containsKey("config.json"));
        assertTrue(entries.containsKey("data-sources.json"));
        assertTrue(entries.containsKey("expressions.json"));
        assertTrue(entries.containsKey("test-data.json"));
        assertFalse(entries.containsKey("coverage-report.json"), "coverage-report.json should be absent on failure");
    }

    // ── importFromZip: with extended files → creates data sources + expressions + test data ──

    @Test
    void importFromZip_withExtendedFiles_createsRecords() throws Exception {
        byte[] zipBytes = buildImportZip(true);
        MockMultipartFile file = new MockMultipartFile("file", "import.zip", "application/zip", zipBytes);

        when(segmentRepository.findAll()).thenReturn(List.of());
        when(segmentRepository.save(any(Segment.class))).thenAnswer(inv -> {
            Segment s = inv.getArgument(0);
            s.setId(50L);
            return s;
        });
        Template saved = createCompositeTemplate(99L, "Imported");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);
        when(dataSourceRepository.save(any(DataSource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expressionRepository.save(any(Expression.class))).thenAnswer(inv -> inv.getArgument(0));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateDTO result = service.importFromZip(file, 1L);

        assertNotNull(result);
        verify(dataSourceRepository, times(1)).save(any(DataSource.class));
        verify(expressionRepository, times(1)).save(any(Expression.class));
        verify(testCaseRepository, times(1)).save(any(TestCase.class));
    }

    // ── importFromZip: old format ZIP (no extended files) → only imports config + segments ──

    @Test
    void importFromZip_oldFormat_noExtendedFiles_noError() throws Exception {
        byte[] zipBytes = buildImportZip(false);
        MockMultipartFile file = new MockMultipartFile("file", "import.zip", "application/zip", zipBytes);

        when(segmentRepository.findAll()).thenReturn(List.of());
        when(segmentRepository.save(any(Segment.class))).thenAnswer(inv -> {
            Segment s = inv.getArgument(0);
            s.setId(50L);
            return s;
        });
        Template saved = createCompositeTemplate(99L, "Imported");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        TemplateDTO result = service.importFromZip(file, 1L);

        assertNotNull(result);
        verify(dataSourceRepository, never()).save(any(DataSource.class));
        verify(expressionRepository, never()).save(any(Expression.class));
        verify(testCaseRepository, never()).save(any(TestCase.class));
    }

    // ── importFromZip: data-sources.json parse failure → WARN log, basic import unaffected ──

    @Test
    void importFromZip_malformedDataSourcesJson_basicImportSucceeds() throws Exception {
        byte[] zipBytes = buildImportZipWithMalformedDataSources();
        MockMultipartFile file = new MockMultipartFile("file", "import.zip", "application/zip", zipBytes);

        when(segmentRepository.findAll()).thenReturn(List.of());
        when(segmentRepository.save(any(Segment.class))).thenAnswer(inv -> {
            Segment s = inv.getArgument(0);
            s.setId(50L);
            return s;
        });
        Template saved = createCompositeTemplate(99L, "Imported");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        TemplateDTO result = service.importFromZip(file, 1L);

        assertNotNull(result);
        assertEquals("Imported", result.getName());
        verify(dataSourceRepository, never()).save(any(DataSource.class));
    }

    // ── maskCredentialFields: DATABASE password ──

    @Test
    void maskCredentialFields_databasePassword() {
        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("password", "mySecret");

        service.maskCredentialFields(config, "DATABASE");

        assertEquals("__CREDENTIAL_PLACEHOLDER__", config.get("password"));
        assertEquals("localhost", config.get("host"));
    }

    // ── maskCredentialFields: HTTP_API apiKey ──

    @Test
    void maskCredentialFields_httpApiKey() {
        Map<String, Object> config = new HashMap<>();
        config.put("url", "https://api.com");
        config.put("apiKey", "key123");

        service.maskCredentialFields(config, "HTTP_API");

        assertEquals("__CREDENTIAL_PLACEHOLDER__", config.get("apiKey"));
        assertEquals("https://api.com", config.get("url"));
    }

    // ── maskCredentialFields: nested auth object ──

    @Test
    void maskCredentialFields_nestedAuthObject() {
        Map<String, Object> auth = new HashMap<>();
        auth.put("apiKey", "nestedKey");
        auth.put("clientSecret", "nestedSecret");
        auth.put("password", "nestedPwd");

        Map<String, Object> config = new HashMap<>();
        config.put("url", "https://api.com");
        config.put("auth", auth);

        service.maskCredentialFields(config, "HTTP_API");

        @SuppressWarnings("unchecked")
        Map<String, Object> maskedAuth = (Map<String, Object>) config.get("auth");
        assertEquals("__CREDENTIAL_PLACEHOLDER__", maskedAuth.get("apiKey"));
        assertEquals("__CREDENTIAL_PLACEHOLDER__", maskedAuth.get("clientSecret"));
        assertEquals("__CREDENTIAL_PLACEHOLDER__", maskedAuth.get("password"));
    }

    // ── maskCredentialFields: no sensitive fields → no changes ──

    @Test
    void maskCredentialFields_noSensitiveFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("url", "https://safe.com");
        config.put("timeout", 30);

        service.maskCredentialFields(config, "HTTP_API");

        assertEquals("https://safe.com", config.get("url"));
        assertEquals(30, config.get("timeout"));
        assertFalse(config.containsKey("password"));
        assertFalse(config.containsKey("apiKey"));
        assertFalse(config.containsKey("clientSecret"));
    }

    // ── maskCredentialFields: DATABASE type does NOT mask password for non-DATABASE type ──

    @Test
    void maskCredentialFields_nonDatabaseType_doesNotMaskPassword() {
        Map<String, Object> config = new HashMap<>();
        config.put("password", "shouldStay");

        service.maskCredentialFields(config, "HTTP_API");

        // password is only masked for DATABASE type at top level
        assertEquals("shouldStay", config.get("password"));
    }

    // ── toMaskedDataSourceMap: preserves metadata fields ──

    @Test
    void toMaskedDataSourceMap_preservesMetadata() {
        DataSource ds = createDataSource(1L, "testDs", "HTTP_API",
                "{\"url\":\"https://example.com\"}");
        ds.setCacheEnabled(true);
        ds.setCacheTtl(600);
        ds.setPriority(5);

        Map<String, Object> result = service.toMaskedDataSourceMap(ds);

        assertEquals("testDs", result.get("name"));
        assertEquals("HTTP_API", result.get("type"));
        assertEquals(true, result.get("cacheEnabled"));
        assertEquals(600, result.get("cacheTtl"));
        assertEquals(5, result.get("priority"));
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

    private Segment createSegment(Long id, String name) {
        Segment segment = new Segment();
        segment.setId(id);
        segment.setTenantId(1L);
        segment.setName(name);
        segment.setFilePath("segments/1/" + name + ".docx");
        segment.setComponent(false);
        return segment;
    }

    private DataSource createDataSource(Long id, String name, String type, String configJson) {
        DataSource ds = new DataSource();
        ds.setId(id);
        ds.setTemplateId(1L);
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson(configJson);
        ds.setCacheEnabled(false);
        ds.setCacheTtl(300);
        ds.setPriority(0);
        return ds;
    }

    private Expression createExpression(Long id, String name, String type, String text, int order) {
        Expression expr = new Expression();
        expr.setId(id);
        expr.setTemplateId(1L);
        expr.setName(name);
        expr.setExpressionType(type);
        expr.setExpressionText(text);
        expr.setDescription("Test expression");
        expr.setExecutionOrder(order);
        return expr;
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

    /**
     * Build a valid import ZIP with config.json + segments/*.docx + optionally extended files.
     */
    private byte[] buildImportZip(boolean includeExtendedFiles) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // config.json
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

            // segments/intro.docx
            zos.putNextEntry(new ZipEntry("segments/intro.docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();

            if (includeExtendedFiles) {
                // data-sources.json
                List<Map<String, Object>> dsList = List.of(Map.of(
                        "name", "testDs", "type", "HTTP_API",
                        "cacheEnabled", false, "cacheTtl", 300, "priority", 0,
                        "config", Map.of("url", "https://api.com")));
                zos.putNextEntry(new ZipEntry("data-sources.json"));
                zos.write(objectMapper.writeValueAsBytes(dsList));
                zos.closeEntry();

                // expressions.json
                List<Map<String, Object>> exprList = List.of(Map.of(
                        "name", "calc", "expressionType", "JAVASCRIPT",
                        "expressionText", "a+b", "description", "sum", "executionOrder", 1));
                zos.putNextEntry(new ZipEntry("expressions.json"));
                zos.write(objectMapper.writeValueAsBytes(exprList));
                zos.closeEntry();

                // test-data.json
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

    /**
     * Build an import ZIP with a malformed data-sources.json.
     */
    private byte[] buildImportZipWithMalformedDataSources() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            CompositeImportExportService.CompositeExportConfig exportConfig =
                    new CompositeImportExportService.CompositeExportConfig();
            exportConfig.setTemplateName("Imported");
            CompositeImportExportService.CompositeExportConfig.SegmentExportEntry seg =
                    new CompositeImportExportService.CompositeExportConfig.SegmentExportEntry();
            seg.setSegmentName("intro");
            seg.setPosition(0);
            seg.setEnabled(true);
            exportConfig.setSegments(List.of(seg));

            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write(objectMapper.writeValueAsBytes(exportConfig));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("segments/intro.docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();

            // Malformed data-sources.json
            zos.putNextEntry(new ZipEntry("data-sources.json"));
            zos.write("this is not valid json{{{".getBytes());
            zos.closeEntry();

            zos.finish();
        }
        return baos.toByteArray();
    }
}
