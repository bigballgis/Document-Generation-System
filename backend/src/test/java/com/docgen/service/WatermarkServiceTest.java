package com.docgen.service;

import com.docgen.dto.ImageWatermarkConfig;
import com.docgen.dto.TextWatermarkConfig;
import com.docgen.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatermarkServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private WatermarkService service;

    private static final byte[] SAMPLE_DOC = "fake-docx-content".getBytes();
    private static final byte[] WATERMARKED_DOC = "watermarked-content".getBytes();

    @BeforeEach
    void setUp() throws Exception {
        service = new WatermarkService(restTemplate);
        // Set the service URL via reflection since @Value won't be processed in unit tests
        Field urlField = WatermarkService.class.getDeclaredField("docxtemplaterServiceUrl");
        urlField.setAccessible(true);
        urlField.set(service, "http://localhost:3000");
    }

    // ── applyTextWatermark ──

    @Test
    @SuppressWarnings("unchecked")
    void applyTextWatermark_success() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(WATERMARKED_DOC, HttpStatus.OK));

        TextWatermarkConfig config = new TextWatermarkConfig("CONFIDENTIAL");
        config.setFontSize(48);
        config.setColor("#FF0000");
        config.setOpacity(0.5);
        config.setRotation(-30);

        byte[] result = service.applyTextWatermark(SAMPLE_DOC, config);

        assertArrayEquals(WATERMARKED_DOC, result);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("http://localhost:3000/watermark"),
                eq(HttpMethod.POST), captor.capture(), eq(byte[].class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("text", body.get("type"));
        assertEquals("CONFIDENTIAL", body.get("text"));
        assertEquals(48, body.get("fontSize"));
        assertEquals("#FF0000", body.get("color"));
        assertEquals(0.5, body.get("opacity"));
        assertEquals(-30.0, body.get("rotation"));
        assertEquals(Base64.getEncoder().encodeToString(SAMPLE_DOC), body.get("document"));
    }

    @Test
    void applyTextWatermark_nullConfig_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(SAMPLE_DOC, (TextWatermarkConfig) null));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
    }

    @Test
    void applyTextWatermark_blankText_throws() {
        TextWatermarkConfig config = new TextWatermarkConfig("  ");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("水印文本"));
    }

    @Test
    void applyTextWatermark_invalidFontSize_throws() {
        TextWatermarkConfig config = new TextWatermarkConfig("test");
        config.setFontSize(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("字体大小"));
    }

    @Test
    void applyTextWatermark_invalidOpacity_throws() {
        TextWatermarkConfig config = new TextWatermarkConfig("test");
        config.setOpacity(1.5);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("透明度"));
    }

    @Test
    void applyTextWatermark_emptyDocument_throws() {
        TextWatermarkConfig config = new TextWatermarkConfig("test");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(new byte[0], config));
        assertEquals("WATERMARK_FAILED", ex.getErrorCode());
    }

    @Test
    void applyTextWatermark_nullDocument_throws() {
        TextWatermarkConfig config = new TextWatermarkConfig("test");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(null, config));
        assertEquals("WATERMARK_FAILED", ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyTextWatermark_serviceReturnsEmpty_throws() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(new byte[0], HttpStatus.OK));

        TextWatermarkConfig config = new TextWatermarkConfig("test");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_FAILED", ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyTextWatermark_serviceUnavailable_throws() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RestClientException("Connection refused"));

        TextWatermarkConfig config = new TextWatermarkConfig("test");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTextWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_FAILED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("水印服务调用失败"));
    }

    // ── applyTextWatermark with dynamic content ──

    @Test
    @SuppressWarnings("unchecked")
    void applyTextWatermark_dynamicContent_resolvesVariables() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(WATERMARKED_DOC, HttpStatus.OK));

        TextWatermarkConfig config = new TextWatermarkConfig("Draft - {username} - {date}");
        Map<String, Object> context = Map.of("username", "Alice", "date", "2024-01-15");

        byte[] result = service.applyTextWatermark(SAMPLE_DOC, config, context);

        assertArrayEquals(WATERMARKED_DOC, result);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(byte[].class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("Draft - Alice - 2024-01-15", body.get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyTextWatermark_dynamicContent_unresolvedVariablesKept() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(WATERMARKED_DOC, HttpStatus.OK));

        TextWatermarkConfig config = new TextWatermarkConfig("{unknown} watermark");
        Map<String, Object> context = Map.of("other", "value");

        service.applyTextWatermark(SAMPLE_DOC, config, context);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(byte[].class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("{unknown} watermark", body.get("text"));
    }

    // ── applyImageWatermark ──

    @Test
    @SuppressWarnings("unchecked")
    void applyImageWatermark_success() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(WATERMARKED_DOC, HttpStatus.OK));

        ImageWatermarkConfig config = new ImageWatermarkConfig("data:image/png;base64,abc123");
        config.setPosition("TOP_RIGHT");
        config.setOpacity(0.2);

        byte[] result = service.applyImageWatermark(SAMPLE_DOC, config);

        assertArrayEquals(WATERMARKED_DOC, result);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("http://localhost:3000/watermark"),
                eq(HttpMethod.POST), captor.capture(), eq(byte[].class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("image", body.get("type"));
        assertEquals("data:image/png;base64,abc123", body.get("imageSource"));
        assertEquals("TOP_RIGHT", body.get("position"));
        assertEquals(0.2, body.get("opacity"));
    }

    @Test
    void applyImageWatermark_nullConfig_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyImageWatermark(SAMPLE_DOC, null));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
    }

    @Test
    void applyImageWatermark_blankImageSource_throws() {
        ImageWatermarkConfig config = new ImageWatermarkConfig("");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyImageWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("水印图片来源"));
    }

    @Test
    void applyImageWatermark_httpUrl_throws() {
        ImageWatermarkConfig config = new ImageWatermarkConfig("https://example.com/logo.png");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyImageWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Remote image URLs"));
    }

    @Test
    void applyImageWatermark_invalidPosition_throws() {
        ImageWatermarkConfig config = new ImageWatermarkConfig("img-data");
        config.setPosition("INVALID");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyImageWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("无效的水印位置"));
    }

    @Test
    void applyImageWatermark_negativeOpacity_throws() {
        ImageWatermarkConfig config = new ImageWatermarkConfig("img-data");
        config.setOpacity(-0.1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyImageWatermark(SAMPLE_DOC, config));
        assertEquals("WATERMARK_INVALID_CONFIG", ex.getErrorCode());
    }

    // ── resolveTemplateVariables ──

    @Test
    void resolveTemplateVariables_nullText_returnsNull() {
        assertNull(service.resolveTemplateVariables(null, Map.of("a", "b")));
    }

    @Test
    void resolveTemplateVariables_emptyContext_returnsOriginal() {
        assertEquals("hello {name}", service.resolveTemplateVariables("hello {name}", Map.of()));
    }

    @Test
    void resolveTemplateVariables_multipleVars() {
        String result = service.resolveTemplateVariables("{a} and {b}",
                Map.of("a", "X", "b", "Y"));
        assertEquals("X and Y", result);
    }

    @Test
    void resolveTemplateVariables_nullContext_returnsOriginal() {
        assertEquals("hello {name}", service.resolveTemplateVariables("hello {name}", null));
    }

    // ── Default values ──

    @Test
    void textWatermarkConfig_defaults() {
        TextWatermarkConfig config = new TextWatermarkConfig("test");
        assertEquals(36, config.getFontSize());
        assertEquals("#CCCCCC", config.getColor());
        assertEquals(0.3, config.getOpacity());
        assertEquals(-45, config.getRotation());
    }

    @Test
    void imageWatermarkConfig_defaults() {
        ImageWatermarkConfig config = new ImageWatermarkConfig("img");
        assertEquals("CENTER", config.getPosition());
        assertEquals(0.3, config.getOpacity());
    }
}
