package com.docgen.service;

import com.docgen.dto.CreateDataSourceRequest;
import com.docgen.dto.DataSourceDTO;
import com.docgen.dto.UpdateDataSourceRequest;
import com.docgen.entity.DataSource;
import com.docgen.entity.DataSourceType;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service handling data source CRUD operations.
 * Sensitive fields in configJson (password, apiKey, token, secret) are
 * encrypted before storage and masked when returned.
 */
@Service
public class DataSourceCrudService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceCrudService.class);

    /**
     * Keys within configJson that are considered sensitive and must be encrypted/masked.
     */
    static final Set<String> SENSITIVE_KEYS = Set.of("password", "apiKey", "token", "secret");

    /**
     * Regex pattern to match JSON key-value pairs for sensitive fields.
     * Matches: "key":"value" or "key": "value" (with optional whitespace).
     */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "\"(" + String.join("|", SENSITIVE_KEYS) + ")\"\\s*:\\s*\"([^\"]*)\""
    );

    private final DataSourceRepository dataSourceRepository;
    private final TemplateRepository templateRepository;
    private final EncryptionService encryptionService;

    public DataSourceCrudService(DataSourceRepository dataSourceRepository,
                                 TemplateRepository templateRepository,
                                 EncryptionService encryptionService) {
        this.dataSourceRepository = dataSourceRepository;
        this.templateRepository = templateRepository;
        this.encryptionService = encryptionService;
    }

    /**
     * Create a new data source for a template.
     * Sensitive fields in configJson are encrypted before saving.
     */
    @Transactional
    public DataSourceDTO createDataSource(Long templateId, CreateDataSourceRequest request) {
        // Verify template exists (tenant filter is already applied by Hibernate)
        templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        validateType(request.getType());

        DataSource ds = new DataSource();
        ds.setTemplateId(templateId);
        ds.setName(request.getName());
        ds.setType(request.getType());
        ds.setConfigJson(encryptSensitiveFields(request.getConfigJson()));
        ds.setCacheEnabled(request.isCacheEnabled());
        ds.setCacheTtl(request.getCacheTtl());
        ds.setPriority(request.getPriority());

        DataSource saved = dataSourceRepository.save(ds);
        log.info("DataSource created: name={}, id={}, templateId={}", saved.getName(), saved.getId(), templateId);
        return toDTO(saved, true);
    }

    /**
     * Get a single data source by ID. Sensitive fields are masked.
     */
    @Transactional(readOnly = true)
    public DataSourceDTO getDataSource(Long id) {
        DataSource ds = findOrThrow(id);
        return toDTO(ds, true);
    }

    /**
     * Get a single data source by ID with raw (encrypted, not masked) configJson.
     * Used internally by services that need to decrypt sensitive fields themselves.
     */
    @Transactional(readOnly = true)
    public DataSourceDTO getDataSourceRaw(Long id) {
        DataSource ds = findOrThrow(id);
        return toDTO(ds, false);
    }

    /**
     * List all data sources for a template. Sensitive fields are masked.
     */
    @Transactional(readOnly = true)
    public List<DataSourceDTO> listDataSources(Long templateId) {
        // Verify template exists
        templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));

        return dataSourceRepository.findByTemplateIdOrderByPriorityDesc(templateId)
                .stream()
                .map(ds -> toDTO(ds, true))
                .toList();
    }

    /**
     * Update an existing data source.
     * If configJson is updated, sensitive fields are re-encrypted.
     */
    @Transactional
    public DataSourceDTO updateDataSource(Long id, UpdateDataSourceRequest request) {
        DataSource ds = findOrThrow(id);

        if (request.getName() != null) {
            ds.setName(request.getName());
        }
        if (request.getType() != null) {
            validateType(request.getType());
            ds.setType(request.getType());
        }
        if (request.getConfigJson() != null) {
            ds.setConfigJson(encryptSensitiveFields(request.getConfigJson()));
        }
        if (request.getCacheEnabled() != null) {
            ds.setCacheEnabled(request.getCacheEnabled());
        }
        if (request.getCacheTtl() != null) {
            ds.setCacheTtl(request.getCacheTtl());
        }
        if (request.getPriority() != null) {
            ds.setPriority(request.getPriority());
        }

        DataSource saved = dataSourceRepository.save(ds);
        log.info("DataSource updated: id={}", saved.getId());
        return toDTO(saved, true);
    }

    /**
     * Delete a data source by ID.
     */
    @Transactional
    public void deleteDataSource(Long id) {
        DataSource ds = findOrThrow(id);
        dataSourceRepository.delete(ds);
        log.info("DataSource deleted: id={}", id);
    }

    // ── Encryption / Masking helpers ──

    /**
     * Encrypt sensitive field values within a configJson string.
     * Matches keys: password, apiKey, token, secret.
     */
    String encryptSensitiveFields(String configJson) {
        if (configJson == null) return null;
        Matcher matcher = SENSITIVE_PATTERN.matcher(configJson);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            String encrypted = value.isEmpty() ? "" : encryptionService.encrypt(value);
            matcher.appendReplacement(sb, "\"" + key + "\":\"" + Matcher.quoteReplacement(encrypted) + "\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Mask sensitive field values within a configJson string for display.
     * First decrypts, then masks the plaintext value.
     */
    String maskSensitiveFields(String configJson) {
        if (configJson == null) return null;
        Matcher matcher = SENSITIVE_PATTERN.matcher(configJson);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String encryptedValue = matcher.group(2);
            String masked;
            if (encryptedValue.isEmpty()) {
                masked = "";
            } else {
                try {
                    String decrypted = encryptionService.decrypt(encryptedValue);
                    masked = encryptionService.mask(decrypted);
                } catch (Exception e) {
                    // If decryption fails, mask the raw value
                    masked = encryptionService.mask(encryptedValue);
                }
            }
            matcher.appendReplacement(sb, "\"" + key + "\":\"" + Matcher.quoteReplacement(masked) + "\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ── Private helpers ──

    private DataSource findOrThrow(Long id) {
        return dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DATASOURCE_NOT_FOUND, "数据源不存在"));
    }

    private void validateType(String type) {
        try {
            DataSourceType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "不支持的数据源类型: " + type + "，支持的类型: HTTP_API, DATABASE, INTERNAL_SYSTEM",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private DataSourceDTO toDTO(DataSource ds, boolean maskSensitive) {
        String configForDisplay = maskSensitive ? maskSensitiveFields(ds.getConfigJson()) : ds.getConfigJson();
        return new DataSourceDTO(
                ds.getId(),
                ds.getTemplateId(),
                ds.getName(),
                ds.getType(),
                configForDisplay,
                ds.isCacheEnabled(),
                ds.getCacheTtl(),
                ds.getPriority(),
                ds.getCreatedAt(),
                ds.getUpdatedAt()
        );
    }
}
