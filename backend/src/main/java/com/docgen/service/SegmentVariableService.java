package com.docgen.service;

import com.docgen.dto.SegmentVariableDTO;
import com.docgen.entity.Segment;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for scanning and listing Docxtemplater variables within a Segment's .docx file.
 * Reuses the variable extraction logic from TemplateVariableService.
 */
@Service
public class SegmentVariableService {

    private static final Logger log = LoggerFactory.getLogger(SegmentVariableService.class);

    /**
     * Pattern to extract Docxtemplater variables from template XML content.
     * Matches: {variable}, {#loop}, {/loop}, {#if condition}, {/if}, {^inverted}, etc.
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\{([#/^]?)([a-zA-Z_][a-zA-Z0-9_.]*)(?:\\s+([a-zA-Z_][a-zA-Z0-9_.]*))?\\}");

    private final SegmentRepository segmentRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public SegmentVariableService(SegmentRepository segmentRepository,
                                  MinioClient minioClient) {
        this.segmentRepository = segmentRepository;
        this.minioClient = minioClient;
    }

    /**
     * Scan a Segment's .docx file for Docxtemplater variables.
     * Extracts word/*.xml entries from the ZIP and matches variable patterns.
     *
     * @param segmentId the segment to scan
     * @return list of discovered variables
     */
    @Transactional(readOnly = true)
    public List<SegmentVariableDTO> scanVariables(Long segmentId) {
        Segment segment = findSegmentOrThrow(segmentId);

        if (segment.getFilePath() == null || segment.getFilePath().isBlank()) {
            log.warn("Segment {} has no file path, returning empty variable list", segmentId);
            return Collections.emptyList();
        }

        Set<String> variableNames = extractVariableNames(segment.getFilePath());

        List<SegmentVariableDTO> result = new ArrayList<>();
        for (String name : variableNames) {
            result.add(new SegmentVariableDTO(name, "STRING", false, null));
        }

        log.info("Scanned variables for segment {}: found={}", segmentId, result.size());
        return result;
    }

    /**
     * List variables for a segment (delegates to scanVariables for fresh scan).
     *
     * @param segmentId the segment to list variables for
     * @return list of variables
     */
    @Transactional(readOnly = true)
    public List<SegmentVariableDTO> listVariables(Long segmentId) {
        return scanVariables(segmentId);
    }

    /**
     * Extract Docxtemplater variable names from a .docx file stored in MinIO.
     * Reuses the same logic as TemplateVariableService.extractVariableNames.
     */
    Set<String> extractVariableNames(String filePath) {
        Set<String> variables = new LinkedHashSet<>();
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(filePath)
                .build())) {

            String xmlContent = extractXmlFromDocx(is);
            Matcher matcher = VARIABLE_PATTERN.matcher(xmlContent);
            while (matcher.find()) {
                String prefix = matcher.group(1);
                String name = matcher.group(2);
                String argument = matcher.group(3);
                // Skip closing tags ({/name})
                if (!"/".equals(prefix)) {
                    if ("if".equals(name) && argument != null) {
                        variables.add(argument);
                    } else {
                        variables.add(name);
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TEMPLATE_VARIABLE_SCAN_FAILED,
                    "扫描段落变量失败: " + e.getMessage(),
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        return variables;
    }

    /**
     * Read all XML parts from a .docx (ZIP) file and concatenate their content.
     */
    private String extractXmlFromDocx(InputStream docxStream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(docxStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("word/") && entryName.endsWith(".xml")) {
                    sb.append(new String(zis.readAllBytes()));
                }
            }
        }
        return sb.toString();
    }

    private Segment findSegmentOrThrow(Long segmentId) {
        return segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SEGMENT_NOT_FOUND, "段落不存在"));
    }
}
