package com.docgen.service;

import com.docgen.dto.ImageWatermarkConfig;
import com.docgen.dto.RenderConfigDocument;
import com.docgen.dto.TextWatermarkConfig;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates {@link RenderConfigDocument} parsed from composite ZIP {@code render-config.json}
 * before persisting to {@code templates.render_config} (REQ-R7-001).
 */
@Component
public class RenderConfigValidator {

    static final int MAX_TEXT_WATERMARK_CHARS = 8_000;
    private static final int MAX_IMAGE_BASE64_DECLARED_CHARS = 5_000_000;

    private final WatermarkService watermarkService;

    public RenderConfigValidator(WatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    /**
     * Ensures the document is safe to store and use at generation time.
     */
    public void validateForImport(RenderConfigDocument doc) {
        if (doc == null) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "render-config.json must not be empty", HttpStatus.BAD_REQUEST);
        }
        if (doc.getSchemaVersion() != RenderConfigDocument.SUPPORTED_SCHEMA_VERSION) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "Unsupported render-config schema version: " + doc.getSchemaVersion()
                            + " (supported: " + RenderConfigDocument.SUPPORTED_SCHEMA_VERSION + ")",
                    HttpStatus.BAD_REQUEST);
        }
        if (doc.getBarcodes() != null && !doc.getBarcodes().isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                    "Barcodes in render-config are not supported yet; omit or use an empty list",
                    HttpStatus.BAD_REQUEST);
        }
        if (doc.getTextWatermark() != null) {
            TextWatermarkConfig t = doc.getTextWatermark();
            if (t.getText() != null && t.getText().length() > MAX_TEXT_WATERMARK_CHARS) {
                throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                        "Text watermark text exceeds maximum length", HttpStatus.BAD_REQUEST);
            }
            watermarkService.validateTextWatermarkConfig(t);
        }
        if (doc.getImageWatermark() != null) {
            ImageWatermarkConfig img = doc.getImageWatermark();
            if (img.getImageSource() != null && img.getImageSource().length() > MAX_IMAGE_BASE64_DECLARED_CHARS) {
                throw new BusinessException(ErrorCode.IMPORT_INVALID_FILE,
                        "Image watermark source exceeds maximum size", HttpStatus.BAD_REQUEST);
            }
            watermarkService.validateImageWatermarkConfig(img);
        }
    }
}
