package com.docgen.service;

import com.docgen.dto.ImageWatermarkConfig;
import com.docgen.dto.RenderConfigDocument;
import com.docgen.dto.TextWatermarkConfig;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class RenderConfigValidatorTest {

    private WatermarkService watermarkService;
    private RenderConfigValidator validator;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        watermarkService = new WatermarkService(restTemplate);
        validator = new RenderConfigValidator(watermarkService);
    }

    @Test
    void validTextOnly_passes() {
        RenderConfigDocument d = new RenderConfigDocument();
        d.setTextWatermark(new TextWatermarkConfig("OK"));
        validator.validateForImport(d);
    }

    @Test
    void barcodesNonEmpty_rejected() {
        RenderConfigDocument d = new RenderConfigDocument();
        d.setBarcodes(List.of("x"));
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateForImport(d));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }

    @Test
    void imageHttpUrl_rejected() {
        RenderConfigDocument d = new RenderConfigDocument();
        ImageWatermarkConfig img = new ImageWatermarkConfig("https://evil.example/x.png");
        d.setImageWatermark(img);
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateForImport(d));
        assertEquals(ErrorCode.WATERMARK_INVALID_CONFIG, ex.getErrorCode());
    }

    @Test
    void wrongSchemaVersion_rejected() {
        RenderConfigDocument d = new RenderConfigDocument();
        d.setSchemaVersion(2);
        d.setTextWatermark(new TextWatermarkConfig("t"));
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateForImport(d));
        assertEquals(ErrorCode.IMPORT_INVALID_FILE, ex.getErrorCode());
    }
}
