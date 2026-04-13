package com.docgen.service;

import com.docgen.dto.CreateDataSourceRequest;
import com.docgen.dto.DataSourceDTO;
import com.docgen.dto.UpdateDataSourceRequest;
import com.docgen.entity.DataSource;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceCrudServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private DataSourceCrudService service;

    private Template template;

    @BeforeEach
    void setUp() {
        template = new Template();
        template.setId(1L);
        template.setTenantId(10L);
        template.setName("Test Template");
    }

    // ── createDataSource ──

    @Test
    void createDataSource_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(encryptionService.encrypt("my-secret")).thenReturn("ENCRYPTED");
        when(encryptionService.decrypt("ENCRYPTED")).thenReturn("my-secret");
        when(encryptionService.mask("my-secret")).thenReturn("my-s****cret");
        when(dataSourceRepository.save(any(DataSource.class))).thenAnswer(inv -> {
            DataSource ds = inv.getArgument(0);
            ds.setId(100L);
            ds.setCreatedAt(Instant.now());
            ds.setUpdatedAt(Instant.now());
            return ds;
        });

        CreateDataSourceRequest request = new CreateDataSourceRequest();
        request.setName("API Source");
        request.setType("HTTP_API");
        request.setConfigJson("{\"url\":\"https://api.example.com\",\"apiKey\":\"my-secret\"}");
        request.setCacheEnabled(true);
        request.setCacheTtl(600);
        request.setPriority(1);

        DataSourceDTO result = service.createDataSource(1L, request);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(1L, result.getTemplateId());
        assertEquals("API Source", result.getName());
        assertEquals("HTTP_API", result.getType());
        assertTrue(result.isCacheEnabled());
        assertEquals(600, result.getCacheTtl());
        assertEquals(1, result.getPriority());

        // Verify sensitive field is masked in the response
        assertTrue(result.getConfigJson().contains("my-s****cret"));
        assertFalse(result.getConfigJson().contains("\"apiKey\":\"my-secret\""));

        // Verify sensitive field was encrypted before saving
        ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
        verify(dataSourceRepository).save(captor.capture());
        assertTrue(captor.getValue().getConfigJson().contains("\"apiKey\":\"ENCRYPTED\""));
    }

    @Test
    void createDataSource_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        CreateDataSourceRequest request = new CreateDataSourceRequest();
        request.setName("Source");
        request.setType("HTTP_API");
        request.setConfigJson("{}");

        assertThrows(ResourceNotFoundException.class, () -> service.createDataSource(999L, request));
    }

    @Test
    void createDataSource_invalidType_throws() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        CreateDataSourceRequest request = new CreateDataSourceRequest();
        request.setName("Source");
        request.setType("INVALID_TYPE");
        request.setConfigJson("{}");

        assertThrows(BusinessException.class, () -> service.createDataSource(1L, request));
    }

    // ── getDataSource ──

    @Test
    void getDataSource_success() {
        DataSource ds = createSampleDataSource();
        when(dataSourceRepository.findById(100L)).thenReturn(Optional.of(ds));
        when(encryptionService.decrypt("ENC_VALUE")).thenReturn("plain-token");
        when(encryptionService.mask("plain-token")).thenReturn("plai****oken");

        DataSourceDTO result = service.getDataSource(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Test Source", result.getName());
        // Sensitive field should be masked
        assertTrue(result.getConfigJson().contains("plai****oken"));
    }

    @Test
    void getDataSource_notFound_throws() {
        when(dataSourceRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getDataSource(999L));
    }

    // ── listDataSources ──

    @Test
    void listDataSources_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        DataSource ds1 = createSampleDataSource();
        ds1.setId(100L);
        DataSource ds2 = createSampleDataSource();
        ds2.setId(101L);
        ds2.setName("Source 2");
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L))
                .thenReturn(List.of(ds1, ds2));
        when(encryptionService.decrypt("ENC_VALUE")).thenReturn("plain");
        when(encryptionService.mask("plain")).thenReturn("*****");

        List<DataSourceDTO> result = service.listDataSources(1L);

        assertEquals(2, result.size());
        assertEquals("Test Source", result.get(0).getName());
        assertEquals("Source 2", result.get(1).getName());
    }

    @Test
    void listDataSources_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listDataSources(999L));
    }

    // ── updateDataSource ──

    @Test
    void updateDataSource_success() {
        DataSource ds = createSampleDataSource();
        when(dataSourceRepository.findById(100L)).thenReturn(Optional.of(ds));
        when(encryptionService.encrypt("new-secret")).thenReturn("NEW_ENC");
        when(encryptionService.decrypt("NEW_ENC")).thenReturn("new-secret");
        when(encryptionService.mask("new-secret")).thenReturn("new-****cret");
        when(dataSourceRepository.save(any(DataSource.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateDataSourceRequest request = new UpdateDataSourceRequest();
        request.setName("Updated Source");
        request.setConfigJson("{\"token\":\"new-secret\"}");
        request.setCacheEnabled(true);

        DataSourceDTO result = service.updateDataSource(100L, request);

        assertEquals("Updated Source", result.getName());
        assertTrue(result.isCacheEnabled());
        assertTrue(result.getConfigJson().contains("new-****cret"));
    }

    @Test
    void updateDataSource_notFound_throws() {
        when(dataSourceRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateDataSource(999L, new UpdateDataSourceRequest()));
    }

    @Test
    void updateDataSource_invalidType_throws() {
        DataSource ds = createSampleDataSource();
        when(dataSourceRepository.findById(100L)).thenReturn(Optional.of(ds));

        UpdateDataSourceRequest request = new UpdateDataSourceRequest();
        request.setType("INVALID");

        assertThrows(BusinessException.class, () -> service.updateDataSource(100L, request));
    }

    // ── deleteDataSource ──

    @Test
    void deleteDataSource_success() {
        DataSource ds = createSampleDataSource();
        when(dataSourceRepository.findById(100L)).thenReturn(Optional.of(ds));

        service.deleteDataSource(100L);

        verify(dataSourceRepository).delete(ds);
    }

    @Test
    void deleteDataSource_notFound_throws() {
        when(dataSourceRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteDataSource(999L));
    }

    // ── encryptSensitiveFields ──

    @Test
    void encryptSensitiveFields_encryptsAllSensitiveKeys() {
        when(encryptionService.encrypt("pass123")).thenReturn("ENC_PASS");
        when(encryptionService.encrypt("key456")).thenReturn("ENC_KEY");
        when(encryptionService.encrypt("tok789")).thenReturn("ENC_TOK");
        when(encryptionService.encrypt("sec000")).thenReturn("ENC_SEC");

        String input = "{\"password\":\"pass123\",\"apiKey\":\"key456\",\"token\":\"tok789\",\"secret\":\"sec000\",\"url\":\"http://example.com\"}";
        String result = service.encryptSensitiveFields(input);

        assertTrue(result.contains("\"password\":\"ENC_PASS\""));
        assertTrue(result.contains("\"apiKey\":\"ENC_KEY\""));
        assertTrue(result.contains("\"token\":\"ENC_TOK\""));
        assertTrue(result.contains("\"secret\":\"ENC_SEC\""));
        assertTrue(result.contains("\"url\":\"http://example.com\""));
    }

    @Test
    void encryptSensitiveFields_noSensitiveKeys_unchanged() {
        String input = "{\"url\":\"http://example.com\",\"method\":\"GET\"}";
        String result = service.encryptSensitiveFields(input);
        assertEquals(input, result);
    }

    @Test
    void encryptSensitiveFields_emptyValue_notEncrypted() {
        String input = "{\"password\":\"\"}";
        String result = service.encryptSensitiveFields(input);
        assertEquals("{\"password\":\"\"}", result);
    }

    @Test
    void encryptSensitiveFields_null_returnsNull() {
        assertNull(service.encryptSensitiveFields(null));
    }

    // ── maskSensitiveFields ──

    @Test
    void maskSensitiveFields_masksDecryptedValues() {
        when(encryptionService.decrypt("ENC_PASS")).thenReturn("password123");
        when(encryptionService.mask("password123")).thenReturn("pass****d123");

        String input = "{\"password\":\"ENC_PASS\",\"url\":\"http://example.com\"}";
        String result = service.maskSensitiveFields(input);

        assertTrue(result.contains("\"password\":\"pass****d123\""));
        assertTrue(result.contains("\"url\":\"http://example.com\""));
    }

    @Test
    void maskSensitiveFields_null_returnsNull() {
        assertNull(service.maskSensitiveFields(null));
    }

    // ── Helpers ──

    private DataSource createSampleDataSource() {
        DataSource ds = new DataSource();
        ds.setId(100L);
        ds.setTemplateId(1L);
        ds.setName("Test Source");
        ds.setType("HTTP_API");
        ds.setConfigJson("{\"url\":\"https://api.example.com\",\"token\":\"ENC_VALUE\"}");
        ds.setCacheEnabled(false);
        ds.setCacheTtl(300);
        ds.setPriority(0);
        ds.setCreatedAt(Instant.now());
        ds.setUpdatedAt(Instant.now());
        return ds;
    }
}
