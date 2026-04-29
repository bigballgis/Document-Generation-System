package com.docgen.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * JSON shape of {@code render-config.json} in composite template ZIP import/export (REQ-R7-001).
 * <p>Schema version 1 supports text and image watermarks only. Remote image URLs are rejected;
 * use inline base64 or {@code data:} URIs per {@link ImageWatermarkConfig}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RenderConfigDocument {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * When omitted in JSON, treated as {@link #SUPPORTED_SCHEMA_VERSION}.
     */
    private Integer schemaVersion;
    private TextWatermarkConfig textWatermark;
    private ImageWatermarkConfig imageWatermark;
    /**
     * Reserved for future barcode features; import accepts only null or an empty list.
     */
    private List<Object> barcodes;

    public int getSchemaVersion() {
        return schemaVersion != null ? schemaVersion : SUPPORTED_SCHEMA_VERSION;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public TextWatermarkConfig getTextWatermark() {
        return textWatermark;
    }

    public void setTextWatermark(TextWatermarkConfig textWatermark) {
        this.textWatermark = textWatermark;
    }

    public ImageWatermarkConfig getImageWatermark() {
        return imageWatermark;
    }

    public void setImageWatermark(ImageWatermarkConfig imageWatermark) {
        this.imageWatermark = imageWatermark;
    }

    public List<Object> getBarcodes() {
        return barcodes;
    }

    public void setBarcodes(List<Object> barcodes) {
        this.barcodes = barcodes;
    }

    /**
     * True when there is nothing to apply at generation time and nothing to export as meaningful config.
     */
    public boolean isEffectivelyEmpty() {
        boolean noWm = textWatermark == null && imageWatermark == null;
        boolean noBarcodes = barcodes == null || barcodes.isEmpty();
        return noWm && noBarcodes;
    }
}
