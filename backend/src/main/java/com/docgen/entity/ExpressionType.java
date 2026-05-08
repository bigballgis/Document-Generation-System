package com.docgen.entity;

/**
 * Stored values use Java enum names; outbound Docxtemplater {@code /evaluate} calls use
 * {@link #toEvaluateApiType()} literals expected by the Node service.
 */
public enum ExpressionType {
    JAVASCRIPT,
    EXCEL_FORMULA;

    /**
     * @return the {@code type} field value for {@code POST /evaluate} on the Docxtemplater service
     */
    public String toEvaluateApiType() {
        return switch (this) {
            case JAVASCRIPT -> "javascript";
            case EXCEL_FORMULA -> "excel";
        };
    }
}
