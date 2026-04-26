package com.docgen.dto;

import java.util.Map;

/**
 * In-memory outcome of rendering a template for automated test execution.
 * Document bytes are not persisted to document storage.
 *
 * @param dataContext resolved data after the parameter validation pipeline
 * @param docxBytes   rendered DOCX binary
 */
public record TemplateTestRenderOutcome(Map<String, Object> dataContext, byte[] docxBytes) {}
