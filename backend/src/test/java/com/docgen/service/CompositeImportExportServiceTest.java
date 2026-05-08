package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.CompositeCoverageReport;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.ComparisonType;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.config.CompositeZipImportProperties;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeImportExportServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private AssemblyConfigService assemblyConfigService;
    @Mock private MinioClient minioClient;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private CompositeCoverageService compositeCoverageService;
    @Mock private ParameterService parameterService;
    @Mock private com.docgen.repository.ParameterRepository parameterRepository;
    @Mock private RenderConfigValidator renderConfigValidator;

    private CompositeImportExportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CompositeZipImportProperties zipImportProperties;

    @BeforeEach
    void setUp() throws Exception {
        zipImportProperties = new CompositeZipImportProperties();
        lenient().doNothing().when(renderConfigValidator).validateForImport(any());
        rebuildService();
        TenantContext.setCurrentTenantId(1L);
    }

    private void rebuildService() throws Exception {
        service = new CompositeImportExportService(
                templateRepository, assemblyConfigService,
                minioClient, objectMapper,
                testCaseRepository, compositeCoverageService,
                parameterService, parameterRepository,
                zipImportProperties,
                renderConfigValidator);
        Field bucketField = CompositeImportExportService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen-test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }


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

    @Test
    void exportAsZip_includesRenderConfigWhenSet() throws Exception {
        Template template = createCompositeTemplate(1L, "TestTemplate");
        template.setRenderConfig("{\"schemaVersion\":1,\"textWatermark\":{\"text\":\"DRAFT\",\"fontSize\":36}}");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        mockEmptyAssemblyConfig();
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        mockCoverageSuccess();

        byte[] zipBytes = service.exportAsZip(1L);
        Map<String, byte[]> entries = extractZipEntries(zipBytes);
        assertTrue(entries.containsKey("render-config.json"));
    }

    @Test
    void importFromZip_withRenderConfig_persistsOnTemplate() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write(objectMapper.writeValueAsBytes(minimalExportConfig()));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("segments/intro.docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("render-config.json"));
            zos.write("{\"schemaVersion\":1,\"textWatermark\":{\"text\":\"IMPORTED\",\"fontSize\":36}}"
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        ArgumentCaptor<Template> templateCaptor = ArgumentCaptor.forClass(Template.class);
        Template saved = createCompositeTemplate(200L, "R");
        when(templateRepository.save(templateCaptor.capture())).thenReturn(saved);

        service.importFromZip(
                new MockMultipartFile("file", "r.zip", "application/zip", baos.toByteArray()), 1L);

        assertNotNull(templateCaptor.getValue().getRenderConfig());
        assertTrue(templateCaptor.getValue().getRenderConfig().contains("IMPORTED"));
        verify(renderConfigValidator).validateForImport(any());
    }

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


    @Test
    void importFromZip_withTestData_createsRecords() throws Exception {
        byte[] zipBytes = buildImportZip(true);
        MockMultipartFile file = new MockMultipartFile("file", "import.zip", "application/zip", zipBytes);

        Template saved = createCompositeTemplate(99L, "Imported");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateDTO result = service.importFromZip(file, 1L);

        assertNotNull(result);
        verify(testCaseRepository, times(1)).save(any(TestCase.class));
    }


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


    @Test
    @DisplayName("Non-ZIP bytes fail parse with IMPORT_INVALID_FILE")
    void importFromZip_nonZipBytes_throwsImportInvalidFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "noise.zip", "application/zip", new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05});

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importFromZip(file, 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Valid empty ZIP (no entries) → missing config.json")
    void importFromZip_emptyZipArchive_throwsMissingConfig() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // no entries
        }
        MockMultipartFile file = new MockMultipartFile("file", "empty.zip", "application/zip", baos.toByteArray());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importFromZip(file, 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("config.json"));
    }

    @Test
    @DisplayName("Malformed config.json → parse error mapped to IMPORT_INVALID_FILE")
    void importFromZip_malformedConfigJson_throwsImportInvalidFile() throws Exception {
        byte[] zipBytes = buildZipWithRawEntry("config.json", "{ not valid json ".getBytes(StandardCharsets.UTF_8),
                Map.of("segments/intro.docx", new byte[]{0x50, 0x4B, 0x03, 0x04}));
        MockMultipartFile file = new MockMultipartFile("file", "bad-config.zip", "application/zip", zipBytes);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importFromZip(file, 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Unknown root-level entry is rejected (strict allowlist)")
    void importFromZip_unknownRootFile_rejected() throws Exception {
        byte[] zipBytes = buildZipWithRawEntry(
                "readme.txt",
                "hello".getBytes(StandardCharsets.UTF_8),
                Map.of(
                        "config.json", objectMapper.writeValueAsBytes(minimalExportConfig()),
                        "segments/intro.docx", new byte[]{0x50, 0x4B, 0x03, 0x04}));
        MockMultipartFile file = new MockMultipartFile("file", "mixed.zip", "application/zip", zipBytes);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importFromZip(file, 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Nested segment path is rejected")
    void importFromZip_nestedSegmentPath_rejected() throws Exception {
        byte[] zipBytes = buildZipWithRawEntry(
                "config.json",
                objectMapper.writeValueAsBytes(minimalExportConfig()),
                Map.of("segments/chapter/intro.docx", new byte[]{0x50, 0x4B, 0x03, 0x04}));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromZip(new MockMultipartFile("file", "nested.zip", "application/zip", zipBytes), 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Zip-slip-style segment entry path is rejected")
    void importFromZip_segmentPathWithDotDot_rejected() throws Exception {
        byte[] zipBytes = buildZipWithRawEntry(
                "config.json",
                objectMapper.writeValueAsBytes(minimalExportConfig()),
                Map.of("segments/../segments/slip.docx", new byte[]{0x50, 0x4B, 0x03, 0x04}));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromZip(new MockMultipartFile("file", "slip.zip", "application/zip", zipBytes), 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Optional coverage-report.json is accepted and drained")
    void importFromZip_withCoverageReport_succeeds() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write(objectMapper.writeValueAsBytes(minimalExportConfig()));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("segments/intro.docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("coverage-report.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Template saved = createCompositeTemplate(55L, "Cov");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        assertNotNull(service.importFromZip(
                new MockMultipartFile("file", "cov.zip", "application/zip", baos.toByteArray()), 1L));
        verify(minioClient, atLeastOnce()).putObject(any());
    }

    @Test
    @DisplayName("Large segment entry still imports under default per-entry cap (WS-03-T02 bounded read)")
    void importFromZip_largeSegmentEntry_importSucceeds() throws Exception {
        int size = 512 * 1024;
        byte[] big = new byte[size];
        big[0] = 0x50;
        big[1] = 0x4B;
        byte[] zipBytes = buildZipWithRawEntry(
                "config.json",
                objectMapper.writeValueAsBytes(minimalExportConfig()),
                Map.of("segments/intro.docx", big));
        MockMultipartFile file = new MockMultipartFile("file", "big.zip", "application/zip", zipBytes);

        Template saved = createCompositeTemplate(77L, "Big");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        TemplateDTO result = service.importFromZip(file, 1L);
        assertNotNull(result);
        verify(minioClient, atLeastOnce()).putObject(any());
    }


    @Test
    void importFromZip_perEntryMaxExceeded_rejected() throws Exception {
        zipImportProperties.setMaxEntryBytes(32);
        rebuildService();

        byte[] zipBytes = buildZipWithRawEntry(
                "config.json",
                objectMapper.writeValueAsBytes(minimalExportConfig()),
                Map.of("segments/intro.docx", new byte[64]));
        MockMultipartFile file = new MockMultipartFile("file", "big-entry.zip", "application/zip", zipBytes);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importFromZip(file, 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    void importFromZip_maxEntryCountExceeded_rejected() throws Exception {
        zipImportProperties.setMaxEntryCount(1);
        rebuildService();

        byte[] zipBytes = buildImportZip(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromZip(new MockMultipartFile("file", "two-entries.zip", "application/zip", zipBytes), 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    void importFromZip_maxTotalUncompressedExceeded_rejected() throws Exception {
        zipImportProperties.setMaxTotalUncompressedBytes(500);
        rebuildService();

        byte[] seg = new byte[300];
        seg[0] = 0x50;
        seg[1] = 0x4B;
        byte[] zipBytes = buildZipWithRawEntry(
                "config.json",
                objectMapper.writeValueAsBytes(twoSegmentExportConfig()),
                Map.of(
                        "segments/a.docx", seg,
                        "segments/b.docx", seg));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromZip(new MockMultipartFile("file", "total.zip", "application/zip", zipBytes), 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    void importFromZip_maxArchiveBytesExceeded_rejected() throws Exception {
        zipImportProperties.setMaxArchiveBytes(20);
        rebuildService();

        byte[] zipBytes = buildImportZip(false);
        assertTrue(zipBytes.length > 20);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromZip(new MockMultipartFile("file", "big.zip", "application/zip", zipBytes), 1L));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }


    @Test
    void importFromZip_segmentNameWithPunctuation_usesSanitizedMinioObjectKey() throws Exception {
        byte[] zipBytes = buildImportZipWithLogicalSegmentName("part!x@y", false);
        Template saved = createCompositeTemplate(501L, "SanitizedSeg");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        service.importFromZip(new MockMultipartFile("file", "p.zip", "application/zip", zipBytes), 1L);

        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, atLeastOnce()).putObject(cap.capture());
        assertTrue(cap.getAllValues().stream().map(PutObjectArgs::object).anyMatch(o ->
                        !o.contains("!")
                                && !o.contains("@")
                                && o.matches("segments/1/[0-9a-fA-F-]{36}_part_x_y\\.docx")),
                () -> cap.getAllValues().stream().map(PutObjectArgs::object).toList().toString());
    }

    @Test
    void importFromZip_introSegment_keepsReadableStorageSuffix() throws Exception {
        byte[] zipBytes = buildImportZip(false);
        Template saved = createCompositeTemplate(502L, "Intro");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        service.importFromZip(new MockMultipartFile("file", "i.zip", "application/zip", zipBytes), 1L);

        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, atLeastOnce()).putObject(cap.capture());
        assertTrue(cap.getAllValues().stream().map(PutObjectArgs::object)
                .anyMatch(o -> o.matches("segments/1/[0-9a-fA-F-]{36}_intro\\.docx")));
    }

    @Test
    void importFromZip_headerNameWithPunctuation_usesSanitizedMinioObjectKey() throws Exception {
        byte[] zipBytes = buildImportZipWithHeader("intro", "x@hdr!");
        Template saved = createCompositeTemplate(503L, "Hdr");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        service.importFromZip(new MockMultipartFile("file", "h.zip", "application/zip", zipBytes), 1L);

        ArgumentCaptor<PutObjectArgs> cap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, atLeastOnce()).putObject(cap.capture());
        assertTrue(cap.getAllValues().stream().map(PutObjectArgs::object).anyMatch(o ->
                o.contains("/headers/")
                        && !o.contains("@")
                        && !o.contains("!")
                        && o.matches(".*headers/[0-9a-fA-F-]{36}_x_hdr\\.docx")));
    }


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

    private byte[] buildImportZipWithLogicalSegmentName(String logicalSegmentName, boolean includeTestData) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            CompositeImportExportService.CompositeExportConfig exportConfig =
                    new CompositeImportExportService.CompositeExportConfig();
            exportConfig.setTemplateName("Imported");
            exportConfig.setTemplateDescription("Test import");
            CompositeImportExportService.CompositeExportConfig.SegmentExportEntry seg =
                    new CompositeImportExportService.CompositeExportConfig.SegmentExportEntry();
            seg.setSegmentName(logicalSegmentName);
            seg.setPosition(0);
            seg.setEnabled(true);
            exportConfig.setSegments(List.of(seg));

            byte[] configBytes = objectMapper.writeValueAsBytes(exportConfig);
            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write(configBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("segments/" + logicalSegmentName + ".docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();

            appendOptionalTestData(zos, includeTestData);
            zos.finish();
        }
        return baos.toByteArray();
    }

    private byte[] buildImportZipWithHeader(String segmentLogical, String headerLogical) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            CompositeImportExportService.CompositeExportConfig exportConfig =
                    new CompositeImportExportService.CompositeExportConfig();
            exportConfig.setTemplateName("Imported");
            exportConfig.setTemplateDescription("Header import");
            CompositeImportExportService.CompositeExportConfig.SegmentExportEntry seg =
                    new CompositeImportExportService.CompositeExportConfig.SegmentExportEntry();
            seg.setSegmentName(segmentLogical);
            seg.setPosition(0);
            seg.setEnabled(true);
            seg.setHeaderFileName(headerLogical);
            exportConfig.setSegments(List.of(seg));

            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write(objectMapper.writeValueAsBytes(exportConfig));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("segments/" + segmentLogical + ".docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("headers/" + headerLogical + ".docx"));
            zos.write(new byte[]{0x50, 0x4B, 0x03, 0x04});
            zos.closeEntry();
            zos.finish();
        }
        return baos.toByteArray();
    }

    private byte[] buildImportZip(boolean includeTestData) throws Exception {
        return buildImportZipWithLogicalSegmentName("intro", includeTestData);
    }

    private void appendOptionalTestData(ZipOutputStream zos, boolean includeTestData) throws Exception {
        if (includeTestData) {
            List<Map<String, Object>> testList = List.of(Map.of(
                    "name", "TC1", "testDataJson", "{\"a\":1}",
                    "expectedResultJson", "{\"r\":2}", "comparisonType", "VARIABLE_VALUE"));
            zos.putNextEntry(new ZipEntry("test-data.json"));
            zos.write(objectMapper.writeValueAsBytes(testList));
            zos.closeEntry();
        }
    }

    private CompositeImportExportService.CompositeExportConfig minimalExportConfig() {
        CompositeImportExportService.CompositeExportConfig exportConfig =
                new CompositeImportExportService.CompositeExportConfig();
        exportConfig.setTemplateName("Imported");
        exportConfig.setTemplateDescription("Boundary test");
        CompositeImportExportService.CompositeExportConfig.SegmentExportEntry seg =
                new CompositeImportExportService.CompositeExportConfig.SegmentExportEntry();
        seg.setSegmentName("intro");
        seg.setPosition(0);
        seg.setEnabled(true);
        exportConfig.setSegments(List.of(seg));
        return exportConfig;
    }

    private CompositeImportExportService.CompositeExportConfig twoSegmentExportConfig() {
        CompositeImportExportService.CompositeExportConfig exportConfig =
                new CompositeImportExportService.CompositeExportConfig();
        exportConfig.setTemplateName("Imported");
        exportConfig.setTemplateDescription("Two segments");
        CompositeImportExportService.CompositeExportConfig.SegmentExportEntry s1 =
                new CompositeImportExportService.CompositeExportConfig.SegmentExportEntry();
        s1.setSegmentName("a");
        s1.setPosition(0);
        s1.setEnabled(true);
        CompositeImportExportService.CompositeExportConfig.SegmentExportEntry s2 =
                new CompositeImportExportService.CompositeExportConfig.SegmentExportEntry();
        s2.setSegmentName("b");
        s2.setPosition(1);
        s2.setEnabled(true);
        exportConfig.setSegments(List.of(s1, s2));
        return exportConfig;
    }

    private byte[] buildZipWithRawEntry(String firstEntryName, byte[] firstEntryBytes,
                                        Map<String, byte[]> additionalEntries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(firstEntryName));
            zos.write(firstEntryBytes);
            zos.closeEntry();
            for (Map.Entry<String, byte[]> e : additionalEntries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

}

