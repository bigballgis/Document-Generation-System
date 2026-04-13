package com.docgen.service;

import com.docgen.dto.ImageWatermarkConfig;
import com.docgen.dto.TextWatermarkConfig;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for applying text and image watermarks to generated documents.
 * <p>
 * Watermark rendering is delegated to the Docxtemplater Node.js service which handles
 * the actual document manipulation. This service handles configuration validation,
 * dynamic content resolution, and orchestration of the remote call.
 */
@Service
public class WatermarkService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkService.class);

    /** Pattern matching template variable placeholders like {variableName}. */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)}");

    private static final Set<String> VALID_POSITIONS = Set.of(
            "CENTER", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT");

    private final RestTemplate restTemplate;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    public WatermarkService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Apply a text watermark to the given document bytes.
     *
     * @param document the rendered document content (DOCX)
     * @param config   text watermark configuration
     * @return document bytes with the text watermark applied
     */
    public byte[] applyTextWatermark(byte[] document, TextWatermarkConfig config) {
        validateTextConfig(config);
        validateDocument(document);

        Map<String, Object> body = new HashMap<>();
        body.put("document", Base64.getEncoder().encodeToString(document));
        body.put("type", "text");
        body.put("text", config.getText());
        body.put("fontSize", config.getFontSize());
        body.put("color", config.getColor());
        body.put("opacity", config.getOpacity());
        body.put("rotation", config.getRotation());

        return callWatermarkEndpoint(body);
    }

    /**
     * Apply a text watermark with dynamic content resolved from the provided data context.
     * Template variable placeholders in the watermark text (e.g. {@code {username}}) are
     * replaced with values from the context map before applying the watermark.
     *
     * @param document the rendered document content (DOCX)
     * @param config   text watermark configuration (text may contain variable placeholders)
     * @param context  data context for resolving template variables
     * @return document bytes with the resolved text watermark applied
     */
    public byte[] applyTextWatermark(byte[] document, TextWatermarkConfig config, Map<String, Object> context) {
        validateTextConfig(config);
        String resolvedText = resolveTemplateVariables(config.getText(), context);
        TextWatermarkConfig resolved = new TextWatermarkConfig(resolvedText);
        resolved.setFontSize(config.getFontSize());
        resolved.setColor(config.getColor());
        resolved.setOpacity(config.getOpacity());
        resolved.setRotation(config.getRotation());
        return applyTextWatermark(document, resolved);
    }

    /**
     * Apply an image watermark to the given document bytes.
     *
     * @param document the rendered document content (DOCX)
     * @param config   image watermark configuration
     * @return document bytes with the image watermark applied
     */
    public byte[] applyImageWatermark(byte[] document, ImageWatermarkConfig config) {
        validateImageConfig(config);
        validateDocument(document);

        Map<String, Object> body = new HashMap<>();
        body.put("document", Base64.getEncoder().encodeToString(document));
        body.put("type", "image");
        body.put("imageSource", config.getImageSource());
        body.put("position", config.getPosition());
        body.put("opacity", config.getOpacity());

        return callWatermarkEndpoint(body);
    }

    // ── Validation ──

    void validateTextConfig(TextWatermarkConfig config) {
        if (config == null) {
            throw new BusinessException(ErrorCode.WATERMARK_INVALID_CONFIG,
                    "文字水印配置不能为空", HttpStatus.BAD_REQUEST);
        }
        if (config.getText() == null || config.getText().isBlank()) {
            throw new BusinessException(ErrorCode.WATERMARK_INVALID_CONFIG,
                    "水印文本不能为空", HttpStatus.BAD_REQUEST);
        }
        if (config.getFontSize() <= 0) {
            throw new BusinessException(ErrorCode.WATERMARK_INVALID_CONFIG,
                    "字体大小必须大于 0", HttpStatus.BAD_REQUEST);
        }
        validateOpacity(config.getOpacity());
    }

    void validateImageConfig(ImageWatermarkConfig config) {
        if (config == null) {
            throw new BusinessException(ErrorCode.WATERMARK_INVALID_CONFIG,
                    "图片水印配置不能为空", HttpStatus.BAD_REQUEST);
        }
        if (config.getImageSource() == null || config.getImageSource().isBlank()) {
            throw new BusinessException(ErrorCode.WATERMARK_INVALID_CONFIG,
                    "水印图片来源不能为空", HttpStatus.BAD_REQUEST);
        }
        if (!VALID_POSITIONS.contains(config.getPosition())) {
            throw new BusinessException(ErrorCode.WATERMARK_INVALID_CONFIG,
                    "无效的水印位置: " + config.getPosition()
                            + "，支持的位置: " + VALID_POSITIONS, HttpStatus.BAD_REQUEST);
        }
        validateOpacity(config.getOpacity());
    }

    private void validateDocument(byte[] document) {
        if (document == null || document.length == 0) {
            throw new BusinessException(ErrorCode.WATERMARK_FAILED,
                    "文档内容不能为空", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateOpacity(double opacity) {
        if (opacity < 0.0 || opacity > 1.0) {
            throw new BusinessException(ErrorCode.WATERMARK_INVALID_CONFIG,
                    "透明度必须在 0.0 到 1.0 之间", HttpStatus.BAD_REQUEST);
        }
    }

    // ── Template variable resolution ──

    /**
     * Replace {@code {variableName}} placeholders in the text with values from the context.
     * Unresolved placeholders are left as-is.
     */
    String resolveTemplateVariables(String text, Map<String, Object> context) {
        if (text == null || context == null || context.isEmpty()) {
            return text;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = context.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    value != null ? String.valueOf(value) : matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ── Remote call ──

    private byte[] callWatermarkEndpoint(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    docxtemplaterServiceUrl + "/watermark",
                    HttpMethod.POST, entity, byte[].class);

            if (response.getBody() == null || response.getBody().length == 0) {
                throw new BusinessException(ErrorCode.WATERMARK_FAILED,
                        "水印服务返回空结果", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return response.getBody();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Watermark service call failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WATERMARK_FAILED,
                    "水印服务调用失败: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("Watermark application failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WATERMARK_FAILED,
                    "水印应用失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
