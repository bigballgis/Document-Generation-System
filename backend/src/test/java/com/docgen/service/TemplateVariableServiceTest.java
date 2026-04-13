package com.docgen.service;

import com.docgen.dto.BindVariableRequest;
import com.docgen.dto.TemplateVariableDTO;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVariable;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVariableRepository;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateVariableServiceTest {

    @Mock
    private TemplateVariableRepository variableRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private MinioClient minioClient;

    private TemplateVariableService service;
    private Template template;

    @BeforeEach
    void setUp() throws Exception {
        service = new TemplateVariableService(variableRepository, templateRepository, minioClient);
        Field bucketField = TemplateVariableService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen");

        template = new Template();
        template.setId(1L);
        template.setTenantId(10L);
        template.setName("Test Template");
        template.setTemplateFilePath("templates/10/test.docx");
    }

    // ── listVariables ──

    @Test
    void listVariables_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        TemplateVariable v1 = createVariable(10L, 1L, "name", false);
        TemplateVariable v2 = createVariable(11L, 1L, "price", true);
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1, v2));

        List<TemplateVariableDTO> result = service.listVariables(1L);

        assertEquals(2, result.size());
        assertEquals("name", result.get(0).getName());
        assertFalse(result.get(0).isBound());
        assertEquals("price", result.get(1).getName());
        assertTrue(result.get(1).isBound());
    }

    @Test
    void listVariables_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listVariables(999L));
    }

    // ── bindVariable ──

    @Test
    void bindVariable_success() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        TemplateVariable variable = createVariable(10L, 1L, "name", false);
        when(variableRepository.findById(10L)).thenReturn(Optional.of(variable));
        when(variableRepository.save(any(TemplateVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        BindVariableRequest request = new BindVariableRequest();
        request.setBindingSource("DATASOURCE");
        request.setBindingField("user.name");

        TemplateVariableDTO result = service.bindVariable(1L, 10L, request);

        assertTrue(result.isBound());
        assertEquals("DATASOURCE", result.getBindingSource());
        assertEquals("user.name", result.getBindingField());
    }

    @Test
    void bindVariable_variableNotFound_throws() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(variableRepository.findById(999L)).thenReturn(Optional.empty());

        BindVariableRequest request = new BindVariableRequest();
        request.setBindingSource("DATASOURCE");
        request.setBindingField("field");

        assertThrows(ResourceNotFoundException.class, () -> service.bindVariable(1L, 999L, request));
    }

    @Test
    void bindVariable_variableNotBelongToTemplate_throws() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        TemplateVariable variable = createVariable(10L, 2L, "name", false); // belongs to template 2
        when(variableRepository.findById(10L)).thenReturn(Optional.of(variable));

        BindVariableRequest request = new BindVariableRequest();
        request.setBindingSource("DATASOURCE");
        request.setBindingField("field");

        assertThrows(BusinessException.class, () -> service.bindVariable(1L, 10L, request));
    }

    // ── extractVariableNames ──

    @Test
    void extractVariableNames_simpleVariables() throws Exception {
        String xml = "<w:t>{name}</w:t><w:t>{age}</w:t><w:t>{email}</w:t>";
        mockMinioDocx(xml);

        Set<String> names = service.extractVariableNames("templates/test.docx");

        assertEquals(Set.of("name", "age", "email"), names);
    }

    @Test
    void extractVariableNames_loopAndConditionTags() throws Exception {
        String xml = "<w:t>{#items}</w:t><w:t>{name}</w:t><w:t>{price}</w:t><w:t>{/items}</w:t>" +
                     "<w:t>{#if showTotal}</w:t><w:t>{total}</w:t><w:t>{/if}</w:t>";
        mockMinioDocx(xml);

        Set<String> names = service.extractVariableNames("templates/test.docx");

        // Loop opener "items" and inner variables
        assertTrue(names.contains("items"));
        assertTrue(names.contains("name"));
        assertTrue(names.contains("price"));
        // {#if showTotal} extracts "showTotal" as the variable
        assertTrue(names.contains("showTotal"));
        assertTrue(names.contains("total"));
        // Closing tags (/items, /if) should not be included
        assertFalse(names.contains("if"));
        assertEquals(5, names.size());
    }

    @Test
    void extractVariableNames_nestedProperties() throws Exception {
        String xml = "<w:t>{user.name}</w:t><w:t>{order.items}</w:t>";
        mockMinioDocx(xml);

        Set<String> names = service.extractVariableNames("templates/test.docx");

        assertTrue(names.contains("user.name"));
        assertTrue(names.contains("order.items"));
    }

    @Test
    void extractVariableNames_duplicatesRemoved() throws Exception {
        String xml = "<w:t>{name}</w:t><w:t>{name}</w:t><w:t>{name}</w:t>";
        mockMinioDocx(xml);

        Set<String> names = service.extractVariableNames("templates/test.docx");

        assertEquals(1, names.size());
        assertTrue(names.contains("name"));
    }

    // ── scanVariables ──

    @Test
    void scanVariables_addsNewAndRemovesStale() throws Exception {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        // Existing variables: "name" (bound), "oldVar" (unbound)
        TemplateVariable existing1 = createVariable(10L, 1L, "name", true);
        TemplateVariable existing2 = createVariable(11L, 1L, "oldVar", false);
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L))
                .thenReturn(List.of(existing1, existing2))
                .thenReturn(List.of(existing1)); // after scan

        // Template now has: "name", "price" (new)
        String xml = "<w:t>{name}</w:t><w:t>{price}</w:t>";
        mockMinioDocx(xml);

        service.scanVariables(1L);

        // "oldVar" should be removed
        verify(variableRepository).deleteAll(argThat(list -> {
            List<TemplateVariable> vars = (List<TemplateVariable>) list;
            return vars.size() == 1 && vars.get(0).getName().equals("oldVar");
        }));

        // "price" should be added
        verify(variableRepository).saveAll(argThat(list -> {
            List<TemplateVariable> vars = (List<TemplateVariable>) list;
            return vars.size() == 1 && vars.get(0).getName().equals("price");
        }));
    }

    // ── Helpers ──

    private TemplateVariable createVariable(Long id, Long templateId, String name, boolean bound) {
        TemplateVariable v = new TemplateVariable();
        v.setId(id);
        v.setTemplateId(templateId);
        v.setName(name);
        v.setVariableType("STRING");
        v.setBound(bound);
        if (bound) {
            v.setBindingSource("DATASOURCE");
            v.setBindingField("data." + name);
        }
        v.setCreatedAt(Instant.now());
        return v;
    }

    private void mockMinioDocx(String xmlContent) throws Exception {
        byte[] docxBytes = createDocxBytes(xmlContent);
        // GetObjectResponse extends FilterInputStream; provide a real InputStream via lenient mock
        GetObjectResponse response = mock(GetObjectResponse.class, withSettings().lenient());
        ByteArrayInputStream bais = new ByteArrayInputStream(docxBytes);
        // Delegate all read methods to the real stream
        when(response.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            int off = inv.getArgument(1);
            int len = inv.getArgument(2);
            return bais.read(buf, off, len);
        });
        when(response.read(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            return bais.read(buf);
        });
        when(response.read()).thenAnswer(inv -> bais.read());
        when(response.available()).thenAnswer(inv -> bais.available());
        doNothing().when(response).close();
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);
    }

    private byte[] createDocxBytes(String xmlContent) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(xmlContent.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
