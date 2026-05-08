package com.docgen.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionTypeTest {

    @Test
    void toEvaluateApiType_javascript() {
        assertEquals("javascript", ExpressionType.JAVASCRIPT.toEvaluateApiType());
    }

    @Test
    void toEvaluateApiType_excelFormula() {
        assertEquals("excel", ExpressionType.EXCEL_FORMULA.toEvaluateApiType());
    }
}
