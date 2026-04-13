package com.docgen.service;

import com.docgen.dto.BindVariableRequest;
import com.docgen.dto.TemplateVariableDTO;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVariable;
import com.docgen.entity.VariableType;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVariableRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for managing template variables: scanning, listing, and binding.
 */
@Service
public class TemplateVariableService {

    private static final Logger log = LoggerFactory.getLogger(TemplateVariableService.class);

    /**
     * Patterns to extract Docxtemplater variables from template XML content.
     * Matches: {variable}, {#loop}, {/loop}, {#if condition}, {/if}, {^inverted}, etc.
     * The tag prefix (#, /, ^) is captured in group 1, the variable name in group 2.
     * Supports optional space-separated arguments after the name (e.g., {#if showTotal}).
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\{([#/^]?)([a-zA-Z_][a-zA-Z0-9_.]*)(?:\\s+([a-zA-Z_][a-zA-Z0-9_.]*))?\\}");

    private final TemplateVariableRepository variableRepository;
    private final TemplateRepository templateRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public TemplateVariableService(TemplateVariableRepository variableRepository,
                                   TemplateRepository templateRepository,
                                   MinioClient minioClient) {
        this.variableRepository = variableRepository;
        this.templateRepository = templateRepository;
        this.minioClient = minioClient;
    }

    /**
     * Scan template file for Docxtemplater variables and sync them to the database.
     * Existing bound variables are preserved; new variables are added as unbound;
     * variables no longer in the template are removed.
     */
    @Transactional
    public List<TemplateVariableDTO> scanVariables(Long templateId) {
        Template template = findTemplateOrThrow(templateId);

        // If no template file has been uploaded yet, return existing variables without scanning
        if (template.getTemplateFilePath() == null || template.getTemplateFilePath().isBlank()) {
            return variableRepository.findByTemplateIdOrderByNameAsc(templateId)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        Set<String> scannedNames = extractVariableNames(template.getTemplateFilePath());
        List<TemplateVariable> existing = variableRepository.findByTemplateIdOrderByNameAsc(templateId);

        Map<String, TemplateVariable> existingMap = new HashMap<>();
        for (TemplateVariable v : existing) {
            existingMap.put(v.getName(), v);
        }

        // Remove variables no longer present in template
        List<TemplateVariable> toRemove = new ArrayList<>();
        for (TemplateVariable v : existing) {
            if (!scannedNames.contains(v.getName())) {
                toRemove.add(v);
            }
        }
        if (!toRemove.isEmpty()) {
            variableRepository.deleteAll(toRemove);
        }

        // Add new variables that don't exist yet
        List<TemplateVariable> toSave = new ArrayList<>();
        for (String name : scannedNames) {
            if (!existingMap.containsKey(name)) {
                TemplateVariable newVar = new TemplateVariable();
                newVar.setTemplateId(templateId);
                newVar.setName(name);
                newVar.setVariableType(VariableType.STRING.name());
                newVar.setBound(false);
                toSave.add(newVar);
            }
        }
        if (!toSave.isEmpty()) {
            variableRepository.saveAll(toSave);
        }

        log.info("Scanned variables for template {}: found={}, new={}, removed={}",
                templateId, scannedNames.size(), toSave.size(), toRemove.size());

        return variableRepository.findByTemplateIdOrderByNameAsc(templateId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * List all variables for a template.
     */
    @Transactional(readOnly = true)
    public List<TemplateVariableDTO> listVariables(Long templateId) {
        findTemplateOrThrow(templateId);
        return variableRepository.findByTemplateIdOrderByNameAsc(templateId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Bind a variable to a data source field or expression result.
     */
    @Transactional
    public TemplateVariableDTO bindVariable(Long templateId, Long variableId, BindVariableRequest request) {
        findTemplateOrThrow(templateId);
        TemplateVariable variable = variableRepository.findById(variableId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VARIABLE_NOT_FOUND, "模板变量不存在"));

        if (!variable.getTemplateId().equals(templateId)) {
            throw new BusinessException(ErrorCode.TEMPLATE_VARIABLE_NOT_FOUND,
                    "变量不属于该模板", HttpStatus.BAD_REQUEST);
        }

        variable.setBindingSource(request.getBindingSource());
        variable.setBindingField(request.getBindingField());
        variable.setBound(true);

        TemplateVariable saved = variableRepository.save(variable);
        log.info("Variable bound: id={}, source={}, field={}", variableId,
                request.getBindingSource(), request.getBindingField());
        return toDTO(saved);
    }

    /**
     * Extract Docxtemplater variable names from a .docx template file stored in MinIO.
     */
    Set<String> extractVariableNames(String templateFilePath) {
        Set<String> variables = new LinkedHashSet<>();
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(templateFilePath)
                .build())) {

            String xmlContent = extractXmlFromDocx(is);
            Matcher matcher = VARIABLE_PATTERN.matcher(xmlContent);
            while (matcher.find()) {
                String prefix = matcher.group(1);
                String name = matcher.group(2);
                String argument = matcher.group(3);
                // Skip closing tags ({/name}) — they don't introduce new variables
                if (!"/".equals(prefix)) {
                    // For tags like {#if showTotal}, "if" is the tag and "showTotal" is the variable
                    if ("if".equals(name) && argument != null) {
                        variables.add(argument);
                    } else {
                        variables.add(name);
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TEMPLATE_VARIABLE_SCAN_FAILED,
                    "扫描模板变量失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        return variables;
    }

    /**
     * Read all XML parts from a .docx (ZIP) file and concatenate their content.
     * Docxtemplater variables can appear in document.xml, header*.xml, footer*.xml, etc.
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

    private Template findTemplateOrThrow(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));
    }

    private TemplateVariableDTO toDTO(TemplateVariable v) {
        return new TemplateVariableDTO(
                v.getId(),
                v.getTemplateId(),
                v.getName(),
                v.getVariableType(),
                v.getDefaultValue(),
                v.getDescription(),
                v.getBindingSource(),
                v.getBindingField(),
                v.isBound(),
                v.getCreatedAt()
        );
    }
}
