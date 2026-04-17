package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates content isolation for .docx files used in composite template segments.
 * Ensures that body, header, and footer content areas do not contain out-of-scope content.
 *
 * <p>Rules:
 * <ul>
 *   <li>"body" → header*.xml and footer*.xml must be empty/absent</li>
 *   <li>"header" → document.xml body and footer*.xml must be empty/absent</li>
 *   <li>"footer" → document.xml body and header*.xml must be empty/absent</li>
 * </ul>
 */
@Service
public class ContentIsolationValidator {

    private static final Logger log = LoggerFactory.getLogger(ContentIsolationValidator.class);

    /**
     * Validate that a .docx file's content stays within the expected content area.
     *
     * @param docxBytes           the .docx file as a byte array
     * @param expectedContentType the expected content type: "body", "header", or "footer"
     * @throws BusinessException if content isolation is violated
     */
    public void validate(byte[] docxBytes, String expectedContentType) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] xmlBytes = zis.readAllBytes();

                if (shouldBeEmpty(name, expectedContentType) && hasNonEmptyTextContent(xmlBytes)) {
                    log.warn("Content isolation violation: {} contains text content for expectedContentType={}",
                            name, expectedContentType);
                    throw new BusinessException(
                            ErrorCode.ONLYOFFICE_CONTENT_ISOLATION_VIOLATION,
                            "内容隔离校验失败: 检测到越界内容",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate content isolation", e);
            throw new BusinessException(
                    ErrorCode.ONLYOFFICE_CONTENT_ISOLATION_VIOLATION,
                    "内容隔离校验失败: " + e.getMessage(),
                    HttpStatus.UNPROCESSABLE_ENTITY, e);
        }
    }

    /**
     * Determine whether a given ZIP entry should be empty based on the expected content type.
     */
    boolean shouldBeEmpty(String entryName, String expectedContentType) {
        if (!entryName.startsWith("word/") || !entryName.endsWith(".xml")) {
            return false;
        }

        String fileName = entryName.substring("word/".length());

        return switch (expectedContentType) {
            case "body" -> fileName.startsWith("header") || fileName.startsWith("footer");
            case "header" -> fileName.equals("document.xml") || fileName.startsWith("footer");
            case "footer" -> fileName.equals("document.xml") || fileName.startsWith("header");
            default -> false;
        };
    }

    /**
     * Check whether XML bytes contain any non-empty {@code <w:t>} text elements.
     * Uses DOM parsing to find w:t elements with actual text content.
     */
    boolean hasNonEmptyTextContent(byte[] xmlBytes) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));

            // Look for w:t elements (WordprocessingML text runs)
            var textNodes = doc.getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t");

            for (int i = 0; i < textNodes.getLength(); i++) {
                String text = textNodes.item(i).getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // If XML parsing fails, treat as non-empty for safety
            log.warn("Failed to parse XML content, treating as non-empty: {}", e.getMessage());
            return true;
        }
    }
}
