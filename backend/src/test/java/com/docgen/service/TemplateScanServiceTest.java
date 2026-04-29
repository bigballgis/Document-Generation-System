package com.docgen.service;

import com.docgen.dto.PlaceholderInfo;
import com.docgen.exception.BusinessException;
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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateScanServiceTest {

    @Mock
    private MinioClient minioClient;

    private TemplateScanService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TemplateScanService(minioClient);
        Field bucketField = TemplateScanService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen");
    }


    @Test
    void parsePlaceholders_simpleVariables() {
        String xml = "<w:t>{name}</w:t><w:t>{age}</w:t><w:t>{email}</w:t>";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(3, result.size());
        assertSimple(result.get(0), "name", "name");
        assertSimple(result.get(1), "age", "age");
        assertSimple(result.get(2), "email", "email");
    }

    @Test
    void parsePlaceholders_deduplicatesSimpleVariables() {
        String xml = "{name}{age}{name}{email}{age}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(3, result.size());
        assertEquals("name", result.get(0).fullPath());
        assertEquals("age", result.get(1).fullPath());
        assertEquals("email", result.get(2).fullPath());
    }


    @Test
    void parsePlaceholders_dotNotation() {
        String xml = "{company.name}{company.address.city}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(2, result.size());

        PlaceholderInfo companyName = result.get(0);
        assertEquals("OBJECT_PATH", companyName.type());
        assertEquals("company.name", companyName.fullPath());
        assertEquals("name", companyName.name());
        assertEquals(List.of("company", "name"), companyName.segments());

        PlaceholderInfo city = result.get(1);
        assertEquals("OBJECT_PATH", city.type());
        assertEquals("company.address.city", city.fullPath());
        assertEquals("city", city.name());
        assertEquals(List.of("company", "address", "city"), city.segments());
    }


    @Test
    void parsePlaceholders_loopConstruct() {
        String xml = "{#items}{name}{price}{/items}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(1, result.size());
        PlaceholderInfo loop = result.get(0);
        assertEquals("LOOP", loop.type());
        assertEquals("items", loop.name());
        assertEquals(2, loop.children().size());
        assertSimple(loop.children().get(0), "name", "name");
        assertSimple(loop.children().get(1), "price", "price");
    }

    @Test
    void parsePlaceholders_nestedLoops() {
        String xml = "{#orders}{orderNo}{#items}{name}{qty}{/items}{/orders}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(1, result.size());
        PlaceholderInfo orders = result.get(0);
        assertEquals("LOOP", orders.type());
        assertEquals("orders", orders.name());
        assertEquals(2, orders.children().size());

        // First child: simple variable orderNo
        assertSimple(orders.children().get(0), "orderNo", "orderNo");

        // Second child: nested loop items
        PlaceholderInfo items = orders.children().get(1);
        assertEquals("LOOP", items.type());
        assertEquals("items", items.name());
        assertEquals(2, items.children().size());
        assertSimple(items.children().get(0), "name", "name");
        assertSimple(items.children().get(1), "qty", "qty");
    }


    @Test
    void parsePlaceholders_condition() {
        String xml = "{#if showTotal}{total}{/if}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(1, result.size());
        PlaceholderInfo cond = result.get(0);
        assertEquals("CONDITION", cond.type());
        assertEquals("showTotal", cond.name());
        assertEquals(1, cond.children().size());
        assertSimple(cond.children().get(0), "total", "total");
    }

    @Test
    void parsePlaceholders_conditionWithMultipleChildren() {
        String xml = "{#if hasDiscount}{discount}{discountRate}{/if}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(1, result.size());
        PlaceholderInfo cond = result.get(0);
        assertEquals("CONDITION", cond.type());
        assertEquals("hasDiscount", cond.name());
        assertEquals(2, cond.children().size());
    }


    @Test
    void parsePlaceholders_mixedContent() {
        String xml = "{title}{#items}{name}{price}{/items}{#if showTotal}{total}{/if}{footer}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(4, result.size());
        assertSimple(result.get(0), "title", "title");
        assertEquals("LOOP", result.get(1).type());
        assertEquals("items", result.get(1).name());
        assertEquals("CONDITION", result.get(2).type());
        assertEquals("showTotal", result.get(2).name());
        assertSimple(result.get(3), "footer", "footer");
    }

    @Test
    void parsePlaceholders_emptyXml() {
        List<PlaceholderInfo> result = service.parsePlaceholders("");
        assertTrue(result.isEmpty());
    }

    @Test
    void parsePlaceholders_noPlaceholders() {
        String xml = "<w:t>Hello World</w:t>";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);
        assertTrue(result.isEmpty());
    }


    @Test
    void parsePlaceholders_dotNotationInsideLoop() {
        String xml = "{#items}{product.name}{product.price}{/items}";
        List<PlaceholderInfo> result = service.parsePlaceholders(xml);

        assertEquals(1, result.size());
        PlaceholderInfo loop = result.get(0);
        assertEquals("LOOP", loop.type());
        assertEquals(2, loop.children().size());
        assertEquals("OBJECT_PATH", loop.children().get(0).type());
        assertEquals("product.name", loop.children().get(0).fullPath());
        assertEquals("OBJECT_PATH", loop.children().get(1).type());
        assertEquals("product.price", loop.children().get(1).fullPath());
    }


    @Test
    void extractXmlFromDocx_readsWordXmlEntries() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<doc>{name}</doc>".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("word/header1.xml"));
            zos.write("<hdr>{header}</hdr>".getBytes());
            zos.closeEntry();
            // Non-word entry should be skipped
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<types/>".getBytes());
            zos.closeEntry();
        }

        String xml = service.extractXmlFromDocx(new ByteArrayInputStream(baos.toByteArray()));
        assertTrue(xml.contains("{name}"));
        assertTrue(xml.contains("{header}"));
        assertFalse(xml.contains("<types/>"));
    }


    @Test
    void scanPlaceholders_success() throws Exception {
        String xml = "{title}{#items}{name}{price}{/items}";
        mockMinioDocx(xml);

        List<PlaceholderInfo> result = service.scanPlaceholders("templates/test.docx");

        assertEquals(2, result.size());
        assertSimple(result.get(0), "title", "title");
        assertEquals("LOOP", result.get(1).type());
    }

    @Test
    void scanPlaceholders_minioError_throwsBusinessException() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.scanPlaceholders("templates/test.docx"));
        assertEquals("PARAMETER_SCAN_FAILED", ex.getErrorCode());
    }


    private void assertSimple(PlaceholderInfo info, String expectedName, String expectedFullPath) {
        assertEquals(expectedName, info.name());
        assertEquals(expectedFullPath, info.fullPath());
        assertEquals("SIMPLE", info.type());
        assertEquals(List.of(expectedName), info.segments());
        assertTrue(info.children().isEmpty());
    }

    private void mockMinioDocx(String xmlContent) throws Exception {
        byte[] docxBytes = createDocxBytes(xmlContent);
        GetObjectResponse response = mock(GetObjectResponse.class, withSettings().lenient());
        ByteArrayInputStream bais = new ByteArrayInputStream(docxBytes);
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

