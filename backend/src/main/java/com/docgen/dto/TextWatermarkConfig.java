package com.docgen.dto;

/**
 * Configuration for text watermarks applied to generated documents.
 * Supports dynamic content via template variable placeholders (e.g. {@code {username}}).
 */
public class TextWatermarkConfig {

    /** Watermark text content. May contain template variable placeholders. */
    private String text;

    /** Font size in points. Defaults to 36. */
    private int fontSize = 36;

    /** Hex colour string, e.g. "#CCCCCC". Defaults to light grey. */
    private String color = "#CCCCCC";

    /** Opacity from 0.0 (fully transparent) to 1.0 (fully opaque). Defaults to 0.3. */
    private double opacity = 0.3;

    /** Rotation angle in degrees (counter-clockwise). Defaults to -45. */
    private double rotation = -45;

    public TextWatermarkConfig() {}

    public TextWatermarkConfig(String text) {
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = fontSize; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = opacity; }

    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; }
}
