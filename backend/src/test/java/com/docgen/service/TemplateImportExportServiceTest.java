package com.docgen.service;

import com.docgen.dto.TemplateConfigExport;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.DataSource;
import com.docgen.entity.Expression;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVariable;
import com.docgen.exception.BusinessException;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVariableRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateImportExportServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private DataSourceRepository dataSourceRepository;
    @Mock private ExpressionRepository expressionRepository;
    @Mock private TemplateVariableRepository templateVariableRepository;
    @Mock private MinioClient minioClient;

    private TemplateImportExportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        service = new TemplateImportExportService(
                templateRepository, dataSourceRepository, expressionRepository,
                templateVariableRepository, minioClient, objectMapper);
        Field bucketField = TemplateImportExportService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen-test");
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── importFromDocx tests ──

    @Test
    void importFromDocx_success() throws Exception {
        // Valid DOCX file (starts with PK\x03\x04 magic bytes)
        byte[] content = createValidDocxBytes();
        MockMultipartFile file = new MockMultipartFile("file", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);

        Template saved = createTemplate(1L, "report");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        TemplateDTO result = service.importFromDocx(file, 100L);

        assertNotNull(result);
        assertEquals("report", result.getName());
        assertEquals("DRAFT", result.getStatus());
        verify(minioClient).putObject(any());
        verify(templateRepository).save(any(Template.class));
    }

    @Test
    void importFromDocx_emptyFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromDocx(file, 100L));
        assertEquals("IMPORT_INVALID_FILE", ex.getErrorCode());
    }

    @Test
    void importFromDocx_wrongExtension_throwsException() {
        byte[] content = createValidDocxBytes();
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf",
                "application/pdf", content);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromDocx(file, 100L));
        assertEquals("IMPORT_INVALID_FILE", ex.getErrorCode());
        assertTrue(ex.getMessage().contains(".docx"));
    }

    @Test
    void importFromDocx_invalidMagicBytes_throwsException() {
        byte[] content = "This is not a docx file".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importFromDocx(file, 100L));
        assertEquals("IMPORT_INVALID_FILE", ex.getErrorCode());
    }

    // ── exportToDocx tests ──

    @Test
    void exportToDocx_success() throws Exception {
        Template template = createTemplate(1L, "Test");
        template.setTemplateFilePath("templates/1/uuid_test.docx");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        byte[] docxContent = createValidDocxBytes();
        GetObjectResponse mockResponse = new GetObjectResponse(
                Headers.of(), "docgen-test", "", "",
                new ByteArrayInputStream(docxContent));
        when(minioClient.getObject(any())).thenReturn(mockResponse);

        byte[] result = service.exportToDocx(1L);

        assertNotNull(result);
        assertEquals(docxContent.length, result.length);
    }

    @Test
    void exportToDocx_templateNotFound_throwsException() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.exportToDocx(999L));
    }

    // ── exportConfig tests ──

    @Test
    void exportConfig_success() throws Exception {
        Template template = createTemplate(1L, "Config Test");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        DataSource ds = new DataSource();
        ds.setName("api-source");
        ds.setType("HTTP");
        ds.setConfigJson("{\"url\":\"https://api.example.com\"}");
        ds.setPriority(1);
        ds.setCacheEnabled(true);
        ds.setCacheTtl(600);
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of(ds));

        Expression expr = new Expression();
        expr.setName("total");
        expr.setExpressionType("JAVASCRIPT");
        expr.setExpressionText("a + b");
        expr.setDescription("Sum");
        expr.setExecutionOrder(1);
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of(expr));

        when(templateVariableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of());

        byte[] result = service.exportConfig(1L);

        assertNotNull(result);
        TemplateConfigExport config = objectMapper.readValue(result, TemplateConfigExport.class);
        assertEquals("1.0", config.getVersion());
        assertEquals("Config Test", config.getTemplate().getName());
        assertEquals(1, config.getDataSources().size());
        assertEquals("api-source", config.getDataSources().get(0).getName());
        assertEquals(1, config.getExpressions().size());
        assertEquals("total", config.getExpressions().get(0).getName());
    }

    // ── importConfig tests ──

    @Test
    void importConfig_success() throws Exception {
        TemplateConfigExport config = new TemplateConfigExport();
        TemplateConfigExport.TemplateMetadata meta = new TemplateConfigExport.TemplateMetadata();
        meta.setName("Imported Template");
        meta.setDescription("Imported desc");
        meta.setOutputFormat("PDF");
        meta.setStorageStrategy("PERSISTENT");
        config.setTemplate(meta);

        TemplateConfigExport.DataSourceExport dsExport = new TemplateConfigExport.DataSourceExport();
        dsExport.setName("db-source");
        dsExport.setType("DATABASE");
        dsExport.setConfigJson("{\"host\":\"localhost\"}");
        dsExport.setPriority(0);
        config.setDataSources(List.of(dsExport));

        TemplateConfigExport.ExpressionExport exprExport = new TemplateConfigExport.ExpressionExport();
        exprExport.setName("calc");
        exprExport.setExpressionType("EXCEL");
        exprExport.setExpressionText("SUM(A1:A10)");
        exprExport.setExecutionOrder(0);
        config.setExpressions(List.of(exprExport));

        byte[] jsonBytes = objectMapper.writeValueAsBytes(config);
        MockMultipartFile file = new MockMultipartFile("file", "config.json",
                "application/json", jsonBytes);

        Template saved = createTemplate(10L, "Imported Template");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);
        when(dataSourceRepository.save(any(DataSource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expressionRepository.save(any(Expression.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateDTO result = service.importConfig(file, 100L);

        assertNotNull(result);
        assertEquals("Imported Template", result.getName());
        verify(dataSourceRepository).save(any(DataSource.class));
        verify(expressionRepository).save(any(Expression.class));
    }

    @Test
    void importConfig_emptyFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "config.json",
                "application/json", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importConfig(file, 100L));
        assertEquals("IMPORT_INVALID_CONFIG", ex.getErrorCode());
    }

    @Test
    void importConfig_invalidJson_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "config.json",
                "application/json", "not valid json{{{".getBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importConfig(file, 100L));
        assertEquals("IMPORT_INVALID_CONFIG", ex.getErrorCode());
    }

    @Test
    void importConfig_missingTemplateName_throwsException() throws Exception {
        TemplateConfigExport config = new TemplateConfigExport();
        config.setTemplate(new TemplateConfigExport.TemplateMetadata()); // name is null

        byte[] jsonBytes = objectMapper.writeValueAsBytes(config);
        MockMultipartFile file = new MockMultipartFile("file", "config.json",
                "application/json", jsonBytes);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importConfig(file, 100L));
        assertEquals("IMPORT_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("template.name"));
    }

    @Test
    void importConfig_missingTemplateField_throwsException() throws Exception {
        // Config with no template field at all
        String json = "{\"version\":\"1.0\",\"dataSources\":[]}";
        MockMultipartFile file = new MockMultipartFile("file", "config.json",
                "application/json", json.getBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importConfig(file, 100L));
        assertEquals("IMPORT_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("template"));
    }

    @Test
    void importConfig_invalidDataSource_throwsException() throws Exception {
        TemplateConfigExport config = new TemplateConfigExport();
        TemplateConfigExport.TemplateMetadata meta = new TemplateConfigExport.TemplateMetadata();
        meta.setName("Valid Name");
        config.setTemplate(meta);

        TemplateConfigExport.DataSourceExport ds = new TemplateConfigExport.DataSourceExport();
        // Missing name and type
        config.setDataSources(List.of(ds));

        byte[] jsonBytes = objectMapper.writeValueAsBytes(config);
        MockMultipartFile file = new MockMultipartFile("file", "config.json",
                "application/json", jsonBytes);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importConfig(file, 100L));
        assertEquals("IMPORT_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("dataSources[0].name"));
        assertTrue(ex.getMessage().contains("dataSources[0].type"));
    }

    @Test
    void importConfig_invalidExpression_throwsException() throws Exception {
        TemplateConfigExport config = new TemplateConfigExport();
        TemplateConfigExport.TemplateMetadata meta = new TemplateConfigExport.TemplateMetadata();
        meta.setName("Valid Name");
        config.setTemplate(meta);

        TemplateConfigExport.ExpressionExport expr = new TemplateConfigExport.ExpressionExport();
        // Missing name, type, text
        config.setExpressions(List.of(expr));

        byte[] jsonBytes = objectMapper.writeValueAsBytes(config);
        MockMultipartFile file = new MockMultipartFile("file", "config.json",
                "application/json", jsonBytes);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importConfig(file, 100L));
        assertEquals("IMPORT_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("expressions[0].name"));
    }

    // ── Helper methods ──

    private byte[] createValidDocxBytes() {
        // Minimal valid ZIP/DOCX header (PK\x03\x04) followed by some data
        return new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00,
                0x08, 0x00, 0x00, 0x00, 0x21, 0x00, 0x00, 0x00};
    }

    private Template createTemplate(Long id, String name) {
        Template template = new Template();
        template.setId(id);
        template.setTenantId(1L);
        template.setName(name);
        template.setDescription("Test description");
        template.setTemplateFilePath("templates/1/uuid_" + name + ".docx");
        template.setOutputFormat("WORD");
        template.setStorageStrategy("TEMP");
        template.setCreatedBy(100L);
        template.setStatus("DRAFT");
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }
}
