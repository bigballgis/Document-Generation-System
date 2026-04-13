package com.docgen.dto;

/**
 * Configuration for image watermarks applied to generated documents.
 */
public class ImageWatermarkConfig {

    /** Base64-encoded image data or a URL pointing to the watermark image. */
    private String imageSource;

    /**
     * Position of the image watermark on the page.
     * Supported values: CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.
     * Defaults to CENTER.
     */
    private String position = "CENTER";

    /** Opacity from 0.0 (fully transparent) to 1.0 (fully opaque). Defaults to 0.3. */
    private double opacity = 0.3;

    public ImageWatermarkConfig() {}

    public ImageWatermarkConfig(String imageSource) {
        this.imageSource = imageSource;
    }

    public String getImageSource() { return imageSource; }
    public void setImageSource(String imageSource) { this.imageSource = imageSource; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = opacity; }
}
