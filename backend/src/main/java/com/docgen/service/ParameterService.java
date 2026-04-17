package com.docgen.service;

import com.docgen.dto.BatchUpdateParameterRequest;
import com.docgen.dto.CreateParameterRequest;
import com.docgen.dto.ExpressionValidationResult;
import com.docgen.dto.ParameterDTO;
import com.docgen.dto.ParameterSchemaDTO;
import com.docgen.dto.ParameterSchemaEntry;
import com.docgen.dto.PlaceholderInfo;
import com.docgen.dto.ScanResultDTO;
import com.docgen.dto.UpdateParameterRequest;
import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Service handling parameter definition CRUD, tree building, and validation.
 */
@Service
public class ParameterService {

    private static final Logger log = LoggerFactory.getLogger(ParameterService.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");
    private static final int MAX_DEPTH = 5;

    private static final Set<String> VALID_PARAMETER_TYPES = Set.of("REQUEST", "DERIVED");
    private static final Set<String> VALID_DATA_TYPES = Set.of("STRING", "NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT");
    private static final Set<String> VALID_EXPRESSION_TYPES = Set.of("JAVASCRIPT", "EXCEL_FORMULA");
    private static final Set<String> CONTAINER_DATA_TYPES = Set.of("OBJECT", "ARRAY");

    // Validation rule keys recognized by the system
    private static final Set<String> RECOGNIZED_RULE_KEYS = Set.of(
            "not_null", "not_blank", "min_length", "max_length",
            "min", "max", "pattern", "enum_values",
            "min_items", "max_items", "date_format", "date_before", "date_after",
            "custom_message"
    );

    // Compatibility matrix: which rules are allowed for which data types
    // Using Map.ofEntries because Map.of only supports up to 10 entries
    private static final Map<String, Set<String>> RULE_COMPATIBILITY = Map.ofEntries(
            Map.entry("not_null", Set.of("STRING", "NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT")),
            Map.entry("not_blank", Set.of("STRING")),
            Map.entry("min_length", Set.of("STRING")),
            Map.entry("max_length", Set.of("STRING")),
            Map.entry("min", Set.of("NUMBER")),
            Map.entry("max", Set.of("NUMBER")),
            Map.entry("pattern", Set.of("STRING")),
            Map.entry("enum_values", Set.of("STRING", "NUMBER")),
            Map.entry("min_items", Set.of("ARRAY")),
            Map.entry("max_items", Set.of("ARRAY")),
            Map.entry("date_format", Set.of("DATE")),
            Map.entry("date_before", Set.of("DATE")),
            Map.entry("date_after", Set.of("DATE"))
    );

    private final ParameterRepository parameterRepository;
    private final TemplateRepository templateRepository;
    private final TemplateScanService templateScanService;
    private final ExpressionEngine expressionEngine;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public ParameterService(ParameterRepository parameterRepository,
                            TemplateRepository templateRepository,
                            TemplateScanService templateScanService,
                            ExpressionEngine expressionEngine,
                            ObjectMapper objectMapper,
                            AuditLogService auditLogService) {
        this.parameterRepository = parameterRepository;
        this.templateRepository = templateRepository;
        this.templateScanService = templateScanService;
        this.expressionEngine = expressionEngine;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    // ── CRUD Methods ──

    /**
     * Create a new parameter definition under the given template.
     */
    @Transactional
    public ParameterDTO createParameter(Long templateId, CreateParameterRequest req) {
        // Validate name pattern
        validateName(req.name());

        // Resolve parameter type and data type with defaults
        String parameterType = req.parameterType() != null ? req.parameterType() : "REQUEST";
        String dataType = req.dataType() != null ? req.dataType() : "STRING";

        if (!VALID_PARAMETER_TYPES.contains(parameterType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "无效的参数类型: " + parameterType, HttpStatus.BAD_REQUEST);
        }
        if (!VALID_DATA_TYPES.contains(dataType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "无效的数据类型: " + dataType, HttpStatus.BAD_REQUEST);
        }

        // Validate parent constraints
        if (req.parentId() != null) {
            ParameterDefinition parent = parameterRepository.findById(req.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMETER_INVALID_PARENT,
                            "父参数不存在: " + req.parentId(), HttpStatus.BAD_REQUEST));
            if (!parent.getTemplateId().equals(templateId)) {
                throw new BusinessException(ErrorCode.PARAMETER_INVALID_PARENT,
                        "父参数不属于当前模板", HttpStatus.BAD_REQUEST);
            }
            validateParentType(parent);
            validateDepth(req.parentId());
        }

        // Validate derived expression constraints
        validateDerivedExpression(parameterType, req.expressionText(), req.expressionType());

        // Detect circular dependency for DERIVED parameters
        if ("DERIVED".equals(parameterType) && req.expressionText() != null) {
            detectCircularDependency(templateId, req.expressionText(), req.name());
        }

        // Validate validation rules compatibility
        if (req.validationRules() != null && !req.validationRules().isEmpty()) {
            validateValidationRules(dataType, req.validationRules());
        }

        // Check duplicate name within scope
        checkDuplicateName(templateId, req.parentId(), req.name());

        // Build and save entity
        ParameterDefinition entity = new ParameterDefinition();
        entity.setTemplateId(templateId);
        entity.setParentId(req.parentId());
        entity.setName(req.name());
        entity.setParameterType(parameterType);
        entity.setDataType(dataType);
        entity.setRequired(req.required() != null ? req.required() : false);
        entity.setDefaultValue(req.defaultValue());
        entity.setDescription(req.description());
        entity.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        entity.setExpressionText(req.expressionText());
        entity.setExpressionType(req.expressionType());
        entity.setValidationRules(serializeValidationRules(req.validationRules()));

        ParameterDefinition saved = parameterRepository.save(entity);
        log.info("Parameter created: id={}, name={}, templateId={}", saved.getId(), saved.getName(), templateId);
        return toDTO(saved);
    }

    /**
     * Get all parameters for a template as a nested tree structure.
     * Children are ordered by sortOrder at each level.
     */
    @Transactional(readOnly = true)
    public List<ParameterDTO> getParameterTree(Long templateId) {
        List<ParameterDefinition> allParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        return buildTree(allParams);
    }

    /**
     * Get all parameters for a template as a flat list with computed parameterPath.
     */
    @Transactional(readOnly = true)
    public List<ParameterDTO> getParameterFlat(Long templateId) {
        List<ParameterDefinition> allParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);

        // Build a lookup map for path computation
        Map<Long, ParameterDefinition> paramMap = allParams.stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, p -> p));

        return allParams.stream().map(param -> {
            ParameterDTO dto = toDTO(param);
            dto.setParameterPath(computeParameterPath(param, paramMap));
            dto.setChildren(null); // flat list has no children nesting
            return dto;
        }).toList();
    }

    /**
     * Update an existing parameter definition.
     */
    @Transactional
    public ParameterDTO updateParameter(Long id, UpdateParameterRequest req) {
        ParameterDefinition entity;
        try {
            entity = parameterRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.PARAMETER_NOT_FOUND, "参数不存在: " + id));

            // Apply updates
            if (req.name() != null) {
                validateName(req.name());
                // Check duplicate only if name changed
                if (!req.name().equals(entity.getName())) {
                    checkDuplicateName(entity.getTemplateId(), entity.getParentId(), req.name());
                }
                entity.setName(req.name());
            }

            String parameterType = req.parameterType() != null ? req.parameterType() : entity.getParameterType();
            String dataType = req.dataType() != null ? req.dataType() : entity.getDataType();

            if (req.parameterType() != null) {
                if (!VALID_PARAMETER_TYPES.contains(req.parameterType())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "无效的参数类型: " + req.parameterType(), HttpStatus.BAD_REQUEST);
                }
                entity.setParameterType(req.parameterType());
            }
            if (req.dataType() != null) {
                if (!VALID_DATA_TYPES.contains(req.dataType())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "无效的数据类型: " + req.dataType(), HttpStatus.BAD_REQUEST);
                }
                entity.setDataType(req.dataType());
            }

            // Validate derived expression constraints with resolved types
            String expressionText = req.expressionText() != null ? req.expressionText() : entity.getExpressionText();
            String expressionType = req.expressionType() != null ? req.expressionType() : entity.getExpressionType();
            validateDerivedExpression(parameterType, expressionText, expressionType);

            // Detect circular dependency for DERIVED parameters
            if ("DERIVED".equals(parameterType) && expressionText != null && !expressionText.isBlank()) {
                detectCircularDependency(entity.getTemplateId(), expressionText, entity.getName());
            }

            if (req.expressionText() != null) {
                entity.setExpressionText(req.expressionText());
            }
            if (req.expressionType() != null) {
                entity.setExpressionType(req.expressionType());
            }

            if (req.required() != null) {
                entity.setRequired(req.required());
            }
            if (req.defaultValue() != null) {
                entity.setDefaultValue(req.defaultValue());
            }
            if (req.description() != null) {
                entity.setDescription(req.description());
            }
            if (req.sortOrder() != null) {
                entity.setSortOrder(req.sortOrder());
            }

            // Validate validation rules compatibility with resolved data type
            if (req.validationRules() != null) {
                validateValidationRules(dataType, req.validationRules());
                entity.setValidationRules(serializeValidationRules(req.validationRules()));
            }

            // Set version for optimistic locking
            entity.setVersion(req.version());

            ParameterDefinition saved = parameterRepository.save(entity);
            log.info("Parameter updated: id={}", saved.getId());
            return toDTO(saved);

        } catch (jakarta.persistence.OptimisticLockException e) {
            throw new BusinessException(ErrorCode.PARAMETER_CONCURRENT_MODIFICATION,
                    "参数已被其他用户修改，请刷新后重试", HttpStatus.CONFLICT, e);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.PARAMETER_CONCURRENT_MODIFICATION,
                    "参数已被其他用户修改，请刷新后重试", HttpStatus.CONFLICT, e);
        }
    }

    /**
     * Delete a parameter by id. Cascade deletion of children is handled by the database.
     */
    @Transactional
    public void deleteParameter(Long id) {
        ParameterDefinition entity = parameterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PARAMETER_NOT_FOUND, "参数不存在: " + id));
        parameterRepository.delete(entity);
        log.info("Parameter deleted: id={}, name={}", id, entity.getName());
    }

    /**
     * Batch delete parameters by IDs. Validates all IDs belong to the specified template.
     * Filters to top-level IDs only (if list contains both parent and child, only deletes parent;
     * child is handled by CASCADE). Records audit log.
     */
    @Transactional
    public void batchDelete(Long templateId, List<Long> ids) {
        // Fetch all parameters for the given IDs
        List<ParameterDefinition> params = parameterRepository.findAllById(ids);

        // Validate all IDs were found and belong to the specified template
        Set<Long> foundIds = params.stream()
                .map(ParameterDefinition::getId)
                .collect(Collectors.toSet());
        List<Long> invalidIds = ids.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!invalidIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_BATCH_INVALID_IDS,
                    "以下参数ID不存在: " + invalidIds, HttpStatus.BAD_REQUEST);
        }

        List<Long> wrongTemplateIds = params.stream()
                .filter(p -> !p.getTemplateId().equals(templateId))
                .map(ParameterDefinition::getId)
                .toList();
        if (!wrongTemplateIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_BATCH_INVALID_IDS,
                    "以下参数ID不属于当前模板: " + wrongTemplateIds, HttpStatus.BAD_REQUEST);
        }

        // Filter to top-level IDs: if both parent and child are in the list, only keep parent
        Set<Long> idSet = new HashSet<>(ids);
        List<ParameterDefinition> topLevel = params.stream()
                .filter(p -> !isAncestorInSet(p, idSet, params))
                .toList();

        // Delete top-level parameters (children handled by CASCADE)
        parameterRepository.deleteAll(topLevel);

        log.info("Batch deleted {} parameters (top-level) for templateId={}, requested ids={}",
                topLevel.size(), templateId, ids);

        // Record audit log
        Long tenantId = TenantContext.getCurrentTenantId();
        String details = String.format("{\"templateId\":%d,\"deletedIds\":%s,\"topLevelIds\":%s}",
                templateId, ids,
                topLevel.stream().map(ParameterDefinition::getId).toList());
        auditLogService.log(tenantId, null, "BATCH_DELETE_PARAMETER",
                "PARAMETER", templateId, details, null);
    }

    /**
     * Check if any ancestor of the given parameter is also in the ID set.
     */
    private boolean isAncestorInSet(ParameterDefinition param, Set<Long> idSet,
                                     List<ParameterDefinition> allParams) {
        Map<Long, ParameterDefinition> paramMap = allParams.stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, p -> p));
        Long currentParentId = param.getParentId();
        while (currentParentId != null) {
            if (idSet.contains(currentParentId)) {
                return true;
            }
            ParameterDefinition parent = paramMap.get(currentParentId);
            currentParentId = parent != null ? parent.getParentId() : null;
        }
        return false;
    }

    /**
     * Batch update parameters. Validates all IDs belong to the specified template.
     * Applies the same validation logic as updateParameter for each item.
     * If ANY validation fails, collects ALL errors and throws BusinessException.
     * Handles optimistic locking conflicts. Single transaction — all succeed or all fail.
     * Records audit log.
     */
    @Transactional
    public List<ParameterDTO> batchUpdate(Long templateId,
                                           List<BatchUpdateParameterRequest.BatchUpdateItem> items) {
        // Fetch all parameters for the given IDs
        List<Long> ids = items.stream().map(BatchUpdateParameterRequest.BatchUpdateItem::id).toList();
        List<ParameterDefinition> params = parameterRepository.findAllById(ids);
        Map<Long, ParameterDefinition> paramMap = params.stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, p -> p));

        // Validate all IDs exist and belong to the specified template
        List<String> errors = new ArrayList<>();
        for (BatchUpdateParameterRequest.BatchUpdateItem item : items) {
            ParameterDefinition entity = paramMap.get(item.id());
            if (entity == null) {
                errors.add("参数ID不存在: " + item.id());
                continue;
            }
            if (!entity.getTemplateId().equals(templateId)) {
                errors.add("参数ID " + item.id() + " 不属于当前模板");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_BATCH_INVALID_IDS,
                    String.join("; ", errors), HttpStatus.BAD_REQUEST);
        }

        // Collect names that will be changed in this batch (for cross-item duplicate detection)
        // Map: (parentId -> set of names) tracking the final name state after batch
        Map<String, Set<String>> scopeNames = new HashMap<>();

        // Validate each item and collect all errors
        for (BatchUpdateParameterRequest.BatchUpdateItem item : items) {
            ParameterDefinition entity = paramMap.get(item.id());

            // Version mismatch check
            if (!Integer.valueOf(entity.getVersion()).equals(item.version())) {
                throw new BusinessException(ErrorCode.PARAMETER_CONCURRENT_MODIFICATION,
                        "参数 " + item.id() + " 已被其他用户修改，请刷新后重试 (当前版本: "
                                + entity.getVersion() + ", 请求版本: " + item.version() + ")",
                        HttpStatus.CONFLICT);
            }

            // Name validation
            if (item.name() != null) {
                try {
                    validateName(item.name());
                } catch (BusinessException e) {
                    errors.add("参数 " + item.id() + ": " + e.getMessage());
                }

                // Check duplicate name within scope (considering other items in the batch)
                if (!item.name().equals(entity.getName())) {
                    String scopeKey = entity.getTemplateId() + ":" + entity.getParentId();
                    Set<String> namesInScope = scopeNames.computeIfAbsent(scopeKey, k -> {
                        // Initialize with existing names in this scope (excluding items being updated)
                        Set<Long> batchIds = items.stream()
                                .map(BatchUpdateParameterRequest.BatchUpdateItem::id)
                                .collect(Collectors.toSet());
                        List<ParameterDefinition> allInTemplate =
                                parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
                        return allInTemplate.stream()
                                .filter(p -> Objects.equals(p.getParentId(), entity.getParentId()))
                                .filter(p -> !batchIds.contains(p.getId()))
                                .map(ParameterDefinition::getName)
                                .collect(Collectors.toCollection(HashSet::new));
                    });
                    if (namesInScope.contains(item.name())) {
                        errors.add("参数 " + item.id() + ": 同一层级下参数名称已存在: " + item.name());
                    } else {
                        namesInScope.add(item.name());
                    }
                }
            }

            // Parameter type validation
            String parameterType = item.parameterType() != null ? item.parameterType() : entity.getParameterType();
            String dataType = item.dataType() != null ? item.dataType() : entity.getDataType();

            if (item.parameterType() != null && !VALID_PARAMETER_TYPES.contains(item.parameterType())) {
                errors.add("参数 " + item.id() + ": 无效的参数类型: " + item.parameterType());
            }
            if (item.dataType() != null && !VALID_DATA_TYPES.contains(item.dataType())) {
                errors.add("参数 " + item.id() + ": 无效的数据类型: " + item.dataType());
            }

            // Derived expression validation
            String expressionText = item.expressionText() != null ? item.expressionText() : entity.getExpressionText();
            String expressionType = item.expressionType() != null ? item.expressionType() : entity.getExpressionType();
            try {
                validateDerivedExpression(parameterType, expressionText, expressionType);
            } catch (BusinessException e) {
                errors.add("参数 " + item.id() + ": " + e.getMessage());
            }

            // Validation rules compatibility
            if (item.validationRules() != null) {
                try {
                    validateValidationRules(dataType, item.validationRules());
                } catch (BusinessException e) {
                    errors.add("参数 " + item.id() + ": " + e.getMessage());
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_BATCH_VALIDATION_FAILED,
                    String.join("; ", errors), HttpStatus.BAD_REQUEST);
        }

        // Apply updates
        List<ParameterDTO> results = new ArrayList<>();
        try {
            for (BatchUpdateParameterRequest.BatchUpdateItem item : items) {
                ParameterDefinition entity = paramMap.get(item.id());

                if (item.name() != null) {
                    entity.setName(item.name());
                }
                if (item.parameterType() != null) {
                    entity.setParameterType(item.parameterType());
                }
                if (item.dataType() != null) {
                    entity.setDataType(item.dataType());
                }
                if (item.required() != null) {
                    entity.setRequired(item.required());
                }
                if (item.defaultValue() != null) {
                    entity.setDefaultValue(item.defaultValue());
                }
                if (item.description() != null) {
                    entity.setDescription(item.description());
                }
                if (item.sortOrder() != null) {
                    entity.setSortOrder(item.sortOrder());
                }
                if (item.expressionText() != null) {
                    entity.setExpressionText(item.expressionText());
                }
                if (item.expressionType() != null) {
                    entity.setExpressionType(item.expressionType());
                }
                if (item.validationRules() != null) {
                    entity.setValidationRules(serializeValidationRules(item.validationRules()));
                }

                entity.setVersion(item.version());
                ParameterDefinition saved = parameterRepository.save(entity);
                results.add(toDTO(saved));
            }
        } catch (jakarta.persistence.OptimisticLockException e) {
            throw new BusinessException(ErrorCode.PARAMETER_CONCURRENT_MODIFICATION,
                    "参数已被其他用户修改，请刷新后重试", HttpStatus.CONFLICT, e);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.PARAMETER_CONCURRENT_MODIFICATION,
                    "参数已被其他用户修改，请刷新后重试", HttpStatus.CONFLICT, e);
        }

        log.info("Batch updated {} parameters for templateId={}", items.size(), templateId);

        // Record audit log
        Long tenantId = TenantContext.getCurrentTenantId();
        String details = String.format("{\"templateId\":%d,\"updatedIds\":%s}", templateId, ids);
        auditLogService.log(tenantId, null, "BATCH_UPDATE_PARAMETER",
                "PARAMETER", templateId, details, null);

        return results;
    }

    // ── JSON Import ──

    /**
     * Import parameters from a JSON string. Recursively traverses the JSON structure
     * and creates parameters with inferred data types. Max nesting depth is 5 levels;
     * deeper structures are flattened to STRING. Single transaction.
     * Records audit log (action=JSON_IMPORT_PARAMETER, resourceType=PARAMETER).
     */
    @Transactional
    public List<ParameterDTO> jsonImport(Long templateId, String jsonData, Long parentId) {
        // Parse JSON
        Object parsed;
        try {
            parsed = objectMapper.readValue(jsonData, Object.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAMETER_JSON_IMPORT_FAILED,
                    "JSON 解析失败: 位置 " + e.getLocation().getCharOffset() + ", 原因: " + e.getOriginalMessage(),
                    HttpStatus.BAD_REQUEST, e);
        }

        // Check for empty JSON
        if (parsed instanceof Map<?, ?> map && map.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_JSON_IMPORT_FAILED,
                    "JSON 数据为空，无法生成参数", HttpStatus.BAD_REQUEST);
        }
        if (parsed instanceof List<?> list && list.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMETER_JSON_IMPORT_FAILED,
                    "JSON 数据为空，无法生成参数", HttpStatus.BAD_REQUEST);
        }

        // Determine starting depth
        int startDepth = 1;
        if (parentId != null) {
            startDepth = computeDepth(parentId) + 1;
        }

        // Recursively create parameters
        List<ParameterDefinition> created = new ArrayList<>();
        if (parsed instanceof Map<?, ?> map) {
            int sortOrder = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = sanitizeName(String.valueOf(entry.getKey()));
                processJsonValue(templateId, key, entry.getValue(), parentId, startDepth, sortOrder++, created);
            }
        } else if (parsed instanceof List<?> list) {
            // Top-level array: create a single ARRAY parameter named "items"
            processJsonValue(templateId, "items", parsed, parentId, startDepth, 0, created);
        } else {
            // Single primitive value at top level
            processJsonValue(templateId, "value", parsed, parentId, startDepth, 0, created);
        }

        // Record audit log
        Long tenantId = TenantContext.getCurrentTenantId();
        String details = String.format("{\"templateId\":%d,\"parentId\":%s,\"createdCount\":%d}",
                templateId, parentId, created.size());
        auditLogService.log(tenantId, null, "JSON_IMPORT_PARAMETER",
                "PARAMETER", templateId, details, null);

        log.info("JSON import created {} parameters for templateId={}, parentId={}",
                created.size(), templateId, parentId);

        return created.stream().map(this::toDTO).toList();
    }

    /**
     * Process a single JSON value and create the corresponding parameter(s).
     */
    private void processJsonValue(Long templateId, String name, Object value,
                                   Long parentId, int currentDepth, int sortOrder,
                                   List<ParameterDefinition> created) {
        // Beyond max depth: flatten to STRING
        if (currentDepth > MAX_DEPTH) {
            log.warn("JSON import: nesting depth {} exceeds max {}, flattening '{}' to STRING",
                    currentDepth, MAX_DEPTH, name);
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "STRING", true, sortOrder,
                    "超过最大嵌套深度，已扁平化为 STRING: " + truncateValue(value));
            created.add(entity);
            return;
        }

        if (value == null) {
            // null → STRING with required=false
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "STRING", false, sortOrder, null);
            created.add(entity);
        } else if (value instanceof String) {
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "STRING", true, sortOrder, null);
            created.add(entity);
        } else if (value instanceof Number) {
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "NUMBER", true, sortOrder, null);
            created.add(entity);
        } else if (value instanceof Boolean) {
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "BOOLEAN", true, sortOrder, null);
            created.add(entity);
        } else if (value instanceof Map<?, ?> map) {
            // Object → OBJECT with children
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "OBJECT", true, sortOrder, null);
            created.add(entity);
            int childSort = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childName = sanitizeName(String.valueOf(entry.getKey()));
                processJsonValue(templateId, childName, entry.getValue(),
                        entity.getId(), currentDepth + 1, childSort++, created);
            }
        } else if (value instanceof List<?> list) {
            processJsonArray(templateId, name, list, parentId, currentDepth, sortOrder, created);
        }
    }

    /**
     * Process a JSON array value. If it contains objects, create ARRAY with children
     * from the union of all element keys. If only primitives, create ARRAY with no children.
     */
    private void processJsonArray(Long templateId, String name, List<?> list,
                                   Long parentId, int currentDepth, int sortOrder,
                                   List<ParameterDefinition> created) {
        // Check if array contains any object elements
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> objectElements = (List<Map<?, ?>>) (List<?>) list.stream()
                .filter(e -> e instanceof Map)
                .toList();

        if (!objectElements.isEmpty()) {
            // Array with object elements → ARRAY with children from union of keys
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "ARRAY", true, sortOrder, null);
            created.add(entity);

            // Collect union of all keys across all object elements
            Map<String, Object> unionMap = new LinkedHashMap<>();
            for (Map<?, ?> objElement : objectElements) {
                for (Map.Entry<?, ?> entry : objElement.entrySet()) {
                    String key = sanitizeName(String.valueOf(entry.getKey()));
                    unionMap.putIfAbsent(key, entry.getValue());
                }
            }

            int childSort = 0;
            for (Map.Entry<String, Object> entry : unionMap.entrySet()) {
                processJsonValue(templateId, entry.getKey(), entry.getValue(),
                        entity.getId(), currentDepth + 1, childSort++, created);
            }
        } else {
            // Array with only primitive elements → ARRAY with no children
            String elementType = inferPrimitiveArrayElementType(list);
            String description = "数组元素类型: " + elementType;
            ParameterDefinition entity = createJsonImportParameter(
                    templateId, name, parentId, "ARRAY", true, sortOrder, description);
            created.add(entity);
        }
    }

    /**
     * Create and save a parameter for JSON import.
     */
    private ParameterDefinition createJsonImportParameter(Long templateId, String name,
                                                           Long parentId, String dataType,
                                                           boolean required, int sortOrder,
                                                           String description) {
        ParameterDefinition entity = new ParameterDefinition();
        entity.setTemplateId(templateId);
        entity.setName(name);
        entity.setParentId(parentId);
        entity.setParameterType("REQUEST");
        entity.setDataType(dataType);
        entity.setRequired(required);
        entity.setSortOrder(sortOrder);
        entity.setDescription(description);
        return parameterRepository.save(entity);
    }

    /**
     * Sanitize a JSON key to be a valid parameter name.
     * Replaces invalid characters with underscore. If the first character is invalid,
     * prepends an underscore.
     */
    String sanitizeName(String key) {
        if (key == null || key.isEmpty()) {
            return "_empty";
        }
        // Replace invalid characters with underscore
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i == 0) {
                if (Character.isLetter(c) || c == '_') {
                    sb.append(c);
                } else {
                    sb.append('_');
                    if (Character.isDigit(c) || c == '-') {
                        sb.append(c);
                    }
                }
            } else {
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            }
        }
        String result = sb.toString();
        // Validate the result; if still invalid (shouldn't happen), fallback
        if (!NAME_PATTERN.matcher(result).matches()) {
            return "_param";
        }
        return result;
    }

    /**
     * Infer the element type description for a primitive-only array.
     */
    private String inferPrimitiveArrayElementType(List<?> list) {
        Set<String> types = new LinkedHashSet<>();
        for (Object element : list) {
            if (element == null) {
                types.add("null");
            } else if (element instanceof String) {
                types.add("STRING");
            } else if (element instanceof Number) {
                types.add("NUMBER");
            } else if (element instanceof Boolean) {
                types.add("BOOLEAN");
            }
        }
        return types.isEmpty() ? "UNKNOWN" : String.join(", ", types);
    }

    /**
     * Truncate a value for description purposes.
     */
    private String truncateValue(Object value) {
        if (value == null) return "null";
        String str = value.toString();
        return str.length() > 100 ? str.substring(0, 100) + "..." : str;
    }

    // ── Scan & Auto-Create ──

    /**
     * Scan the template .docx file for placeholders and compare with existing parameters.
     * Returns matched, unmatched placeholders, and unused parameters.
     */
    @Transactional(readOnly = true)
    public ScanResultDTO scanPlaceholders(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在: " + templateId));

        String filePath = template.getTemplateFilePath();
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMETER_SCAN_FAILED,
                    "模板尚未上传文件，无法扫描占位符", HttpStatus.BAD_REQUEST);
        }

        // Scan placeholders from the .docx file
        List<PlaceholderInfo> scannedPlaceholders = templateScanService.scanPlaceholders(filePath);

        // Get existing parameters as flat list with computed paths
        List<ParameterDefinition> allParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        Map<Long, ParameterDefinition> paramMap = allParams.stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, p -> p));

        // Build set of existing parameter paths
        Set<String> existingPaths = allParams.stream()
                .map(p -> computeParameterPath(p, paramMap))
                .collect(Collectors.toSet());

        // Collect all placeholder full paths (including nested children from loops)
        Set<String> placeholderPaths = new LinkedHashSet<>();
        collectPlaceholderPaths(scannedPlaceholders, "", placeholderPaths);

        // Partition into matched, unmatched, unused
        List<PlaceholderInfo> matched = new ArrayList<>();
        List<PlaceholderInfo> unmatchedPlaceholders = new ArrayList<>();
        partitionPlaceholders(scannedPlaceholders, "", existingPaths, matched, unmatchedPlaceholders);

        // Unused parameters: parameter paths not found in any placeholder path
        List<ParameterDTO> unusedParameters = allParams.stream()
                .map(p -> {
                    ParameterDTO dto = toDTO(p);
                    dto.setParameterPath(computeParameterPath(p, paramMap));
                    return dto;
                })
                .filter(dto -> !placeholderPaths.contains(dto.getParameterPath()))
                .toList();

        log.info("Scan result for templateId={}: matched={}, unmatched={}, unused={}",
                templateId, matched.size(), unmatchedPlaceholders.size(), unusedParameters.size());

        return new ScanResultDTO(matched, unmatchedPlaceholders, unusedParameters);
    }

    /**
     * Auto-create parameter tree hierarchy for all unmatched placeholders.
     * Creates OBJECT intermediates for dot-notation, ARRAY for loops, STRING leaves
     * with data_type recommendation based on name keywords.
     */
    @Transactional
    public List<ParameterDTO> autoCreateParameters(Long templateId) {
        ScanResultDTO scanResult = scanPlaceholders(templateId);
        List<PlaceholderInfo> unmatched = scanResult.unmatchedPlaceholders();

        if (unmatched.isEmpty()) {
            return Collections.emptyList();
        }

        List<ParameterDTO> created = new ArrayList<>();
        // Track already-created parameters by path to avoid duplicates during this batch
        Map<String, ParameterDefinition> createdByPath = new LinkedHashMap<>();

        // Also load existing parameters for path lookup
        List<ParameterDefinition> existingParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        Map<Long, ParameterDefinition> paramMap = existingParams.stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, p -> p));
        for (ParameterDefinition p : existingParams) {
            createdByPath.put(computeParameterPath(p, paramMap), p);
        }

        for (PlaceholderInfo placeholder : unmatched) {
            createParameterFromPlaceholder(templateId, placeholder, "", createdByPath, created);
        }

        log.info("Auto-created {} parameters for templateId={}", created.size(), templateId);
        return created;
    }

    /**
     * Recommend data_type based on name keywords.
     * price/amount/total/count/qty/quantity → NUMBER
     * date/time/created/updated → DATE
     * is/has/enable/active/flag/show → BOOLEAN
     * default → STRING
     */
    String recommendDataType(String name) {
        if (name == null) {
            return "STRING";
        }
        String lower = name.toLowerCase();

        // NUMBER keywords
        if (lower.contains("price") || lower.contains("amount") || lower.contains("total")
                || lower.contains("count") || lower.contains("qty") || lower.contains("quantity")) {
            return "NUMBER";
        }

        // DATE keywords
        if (lower.contains("date") || lower.contains("time")
                || lower.contains("created") || lower.contains("updated")) {
            return "DATE";
        }

        // BOOLEAN keywords
        if (lower.startsWith("is") || lower.startsWith("has") || lower.startsWith("enable")
                || lower.startsWith("show") || lower.contains("active") || lower.contains("flag")) {
            return "BOOLEAN";
        }

        return "STRING";
    }

    /**
     * Recursively collect all placeholder full paths (including children within loops).
     */
    private void collectPlaceholderPaths(List<PlaceholderInfo> placeholders, String parentPath, Set<String> paths) {
        for (PlaceholderInfo ph : placeholders) {
            String fullPath = buildFullPath(parentPath, ph);
            paths.add(fullPath);
            if (ph.children() != null && !ph.children().isEmpty()) {
                // For LOOP/CONDITION types, children are nested under the loop variable
                String childParentPath = parentPath.isEmpty() ? ph.name() : parentPath + "." + ph.name();
                collectPlaceholderPaths(ph.children(), childParentPath, paths);
            }
        }
    }

    /**
     * Partition placeholders into matched and unmatched based on existing parameter paths.
     */
    private void partitionPlaceholders(List<PlaceholderInfo> placeholders, String parentPath,
                                       Set<String> existingPaths,
                                       List<PlaceholderInfo> matched,
                                       List<PlaceholderInfo> unmatched) {
        for (PlaceholderInfo ph : placeholders) {
            String fullPath = buildFullPath(parentPath, ph);
            boolean isMatched = existingPaths.contains(fullPath);

            // For LOOP/CONDITION with children, also check children
            if (ph.children() != null && !ph.children().isEmpty()) {
                String childParentPath = parentPath.isEmpty() ? ph.name() : parentPath + "." + ph.name();
                List<PlaceholderInfo> matchedChildren = new ArrayList<>();
                List<PlaceholderInfo> unmatchedChildren = new ArrayList<>();
                partitionPlaceholders(ph.children(), childParentPath, existingPaths, matchedChildren, unmatchedChildren);

                if (isMatched && unmatchedChildren.isEmpty()) {
                    matched.add(ph);
                } else {
                    // If the parent is unmatched or has unmatched children, add to unmatched
                    if (!isMatched) {
                        unmatched.add(ph);
                    } else {
                        // Parent matched but some children unmatched — add unmatched children
                        unmatched.addAll(unmatchedChildren);
                        matched.add(new PlaceholderInfo(ph.name(), ph.fullPath(), ph.type(), ph.segments(), matchedChildren));
                    }
                }
            } else {
                if (isMatched) {
                    matched.add(ph);
                } else {
                    unmatched.add(ph);
                }
            }
        }
    }

    /**
     * Build the full path for a placeholder considering its parent path.
     * For OBJECT_PATH types, use the fullPath directly.
     * For SIMPLE/LOOP/CONDITION, prepend parent path.
     */
    private String buildFullPath(String parentPath, PlaceholderInfo ph) {
        if ("OBJECT_PATH".equals(ph.type())) {
            return parentPath.isEmpty() ? ph.fullPath() : parentPath + "." + ph.fullPath();
        }
        return parentPath.isEmpty() ? ph.name() : parentPath + "." + ph.name();
    }

    /**
     * Recursively create parameter tree from a placeholder.
     * Handles dot-notation (OBJECT_PATH), loops (ARRAY), and simple leaves.
     */
    private void createParameterFromPlaceholder(Long templateId, PlaceholderInfo placeholder,
                                                 String parentPath,
                                                 Map<String, ParameterDefinition> createdByPath,
                                                 List<ParameterDTO> created) {
        switch (placeholder.type()) {
            case "OBJECT_PATH" -> createObjectPathParameters(templateId, placeholder, parentPath, createdByPath, created);
            case "LOOP" -> createLoopParameters(templateId, placeholder, parentPath, createdByPath, created);
            case "CONDITION" -> createConditionParameters(templateId, placeholder, parentPath, createdByPath, created);
            default -> createSimpleParameter(templateId, placeholder.name(), parentPath, createdByPath, created);
        }
    }

    /**
     * Create parameters for a dot-notation path (e.g., company.address.city).
     * Intermediate segments become OBJECT, leaf becomes STRING (with recommendation).
     */
    private void createObjectPathParameters(Long templateId, PlaceholderInfo placeholder,
                                             String parentPath,
                                             Map<String, ParameterDefinition> createdByPath,
                                             List<ParameterDTO> created) {
        List<String> segments = placeholder.segments();
        String currentPath = parentPath;

        for (int i = 0; i < segments.size(); i++) {
            String segmentName = segments.get(i);
            String segmentPath = currentPath.isEmpty() ? segmentName : currentPath + "." + segmentName;
            boolean isLeaf = (i == segments.size() - 1);

            if (!createdByPath.containsKey(segmentPath)) {
                Long parentId = currentPath.isEmpty() ? null : findParentId(currentPath, createdByPath);
                String dataType = isLeaf ? recommendDataType(segmentName) : "OBJECT";

                ParameterDefinition entity = createAndSaveParameter(templateId, segmentName, parentId, dataType);
                createdByPath.put(segmentPath, entity);
                // Refresh paramMap for path computation
                created.add(toDTOWithPath(entity, segmentPath));
            }

            currentPath = segmentPath;
        }
    }

    /**
     * Create parameters for a loop construct (e.g., {#items}{name}{price}{/items}).
     * Loop variable becomes ARRAY, children become STRING leaves.
     */
    private void createLoopParameters(Long templateId, PlaceholderInfo placeholder,
                                       String parentPath,
                                       Map<String, ParameterDefinition> createdByPath,
                                       List<ParameterDTO> created) {
        String loopPath = parentPath.isEmpty() ? placeholder.name() : parentPath + "." + placeholder.name();

        // Create the ARRAY parameter if not exists
        if (!createdByPath.containsKey(loopPath)) {
            Long parentId = parentPath.isEmpty() ? null : findParentId(parentPath, createdByPath);
            ParameterDefinition arrayEntity = createAndSaveParameter(templateId, placeholder.name(), parentId, "ARRAY");
            createdByPath.put(loopPath, arrayEntity);
            created.add(toDTOWithPath(arrayEntity, loopPath));
        }

        // Create children under the ARRAY
        if (placeholder.children() != null) {
            for (PlaceholderInfo child : placeholder.children()) {
                createParameterFromPlaceholder(templateId, child, loopPath, createdByPath, created);
            }
        }
    }

    /**
     * Create parameters for a condition construct.
     * The condition variable becomes a BOOLEAN parameter, children are created recursively.
     */
    private void createConditionParameters(Long templateId, PlaceholderInfo placeholder,
                                            String parentPath,
                                            Map<String, ParameterDefinition> createdByPath,
                                            List<ParameterDTO> created) {
        String condPath = parentPath.isEmpty() ? placeholder.name() : parentPath + "." + placeholder.name();

        // Create the condition parameter as BOOLEAN if not exists
        if (!createdByPath.containsKey(condPath)) {
            Long parentId = parentPath.isEmpty() ? null : findParentId(parentPath, createdByPath);
            ParameterDefinition condEntity = createAndSaveParameter(templateId, placeholder.name(), parentId, "BOOLEAN");
            createdByPath.put(condPath, condEntity);
            created.add(toDTOWithPath(condEntity, condPath));
        }

        // Condition children are placeholders used inside the condition block
        // They should be created at the same level (not nested under the condition)
        if (placeholder.children() != null) {
            for (PlaceholderInfo child : placeholder.children()) {
                createParameterFromPlaceholder(templateId, child, parentPath, createdByPath, created);
            }
        }
    }

    /**
     * Create a simple leaf parameter (STRING with data_type recommendation).
     */
    private void createSimpleParameter(Long templateId, String name, String parentPath,
                                        Map<String, ParameterDefinition> createdByPath,
                                        List<ParameterDTO> created) {
        String paramPath = parentPath.isEmpty() ? name : parentPath + "." + name;

        if (!createdByPath.containsKey(paramPath)) {
            Long parentId = parentPath.isEmpty() ? null : findParentId(parentPath, createdByPath);
            String dataType = recommendDataType(name);
            ParameterDefinition entity = createAndSaveParameter(templateId, name, parentId, dataType);
            createdByPath.put(paramPath, entity);
            created.add(toDTOWithPath(entity, paramPath));
        }
    }

    /**
     * Find the parent parameter ID from the path lookup map.
     */
    private Long findParentId(String parentPath, Map<String, ParameterDefinition> createdByPath) {
        ParameterDefinition parent = createdByPath.get(parentPath);
        return parent != null ? parent.getId() : null;
    }

    /**
     * Create and persist a new ParameterDefinition with the given attributes.
     */
    private ParameterDefinition createAndSaveParameter(Long templateId, String name, Long parentId, String dataType) {
        ParameterDefinition entity = new ParameterDefinition();
        entity.setTemplateId(templateId);
        entity.setName(name);
        entity.setParentId(parentId);
        entity.setParameterType("REQUEST");
        entity.setDataType(dataType);
        entity.setRequired(false);
        entity.setSortOrder(0);
        return parameterRepository.save(entity);
    }

    /**
     * Convert entity to DTO with a pre-computed path.
     */
    private ParameterDTO toDTOWithPath(ParameterDefinition entity, String path) {
        ParameterDTO dto = toDTO(entity);
        dto.setParameterPath(path);
        return dto;
    }

    // ── Tree Building ──

    /**
     * Build a nested tree from a flat list of parameters.
     * Groups by parentId and recursively attaches children ordered by sortOrder.
     */
    List<ParameterDTO> buildTree(List<ParameterDefinition> allParams) {
        // Create DTO map and group by parentId
        Map<Long, ParameterDTO> dtoMap = new LinkedHashMap<>();
        Map<Long, List<ParameterDTO>> childrenByParentId = new LinkedHashMap<>();

        for (ParameterDefinition param : allParams) {
            ParameterDTO dto = toDTO(param);
            dtoMap.put(param.getId(), dto);
        }

        // Build a lookup for path computation
        Map<Long, ParameterDefinition> entityMap = allParams.stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, p -> p));

        List<ParameterDTO> roots = new ArrayList<>();
        for (ParameterDefinition param : allParams) {
            ParameterDTO dto = dtoMap.get(param.getId());
            dto.setParameterPath(computeParameterPath(param, entityMap));

            if (param.getParentId() == null) {
                roots.add(dto);
            } else {
                childrenByParentId
                        .computeIfAbsent(param.getParentId(), k -> new ArrayList<>())
                        .add(dto);
            }
        }

        // Attach children recursively
        for (ParameterDTO dto : dtoMap.values()) {
            List<ParameterDTO> children = childrenByParentId.getOrDefault(dto.getId(), Collections.emptyList());
            dto.setChildren(new ArrayList<>(children));
        }

        return roots;
    }

    // ── Path Computation ──

    /**
     * Compute the full parameter path by traversing the parent chain.
     * E.g., root "company" → child "address" → grandchild "city" → "company.address.city"
     */
    public String computeParameterPath(ParameterDefinition param, Map<Long, ParameterDefinition> paramMap) {
        LinkedList<String> segments = new LinkedList<>();
        ParameterDefinition current = param;
        while (current != null) {
            segments.addFirst(current.getName());
            current = current.getParentId() != null ? paramMap.get(current.getParentId()) : null;
        }
        return String.join(".", segments);
    }

    // ── Validation Helpers ──

    /**
     * Validate parameter name matches the required pattern: ^[a-zA-Z_][a-zA-Z0-9_-]*$
     */
    void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.PARAMETER_INVALID_NAME,
                    "参数名称格式无效，必须以字母或下划线开头，只能包含字母、数字、下划线和连字符: " + name,
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Validate that the parent parameter's data_type is OBJECT or ARRAY.
     */
    void validateParentType(ParameterDefinition parent) {
        if (!CONTAINER_DATA_TYPES.contains(parent.getDataType())) {
            throw new BusinessException(ErrorCode.PARAMETER_PARENT_TYPE_INVALID,
                    "只有 OBJECT 或 ARRAY 类型的参数才能包含子参数，当前父参数类型: " + parent.getDataType(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Validate that adding a child under the given parent would not exceed MAX_DEPTH (5 levels).
     * Root level counts as level 1.
     */
    void validateDepth(Long parentId) {
        int depth = computeDepth(parentId);
        if (depth + 1 > MAX_DEPTH) {
            throw new BusinessException(ErrorCode.PARAMETER_MAX_DEPTH_EXCEEDED,
                    "参数嵌套深度不能超过 " + MAX_DEPTH + " 层",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Compute the depth of a parameter by traversing the parent chain.
     * Root level = 1.
     */
    private int computeDepth(Long paramId) {
        int depth = 0;
        Long currentId = paramId;
        while (currentId != null) {
            depth++;
            ParameterDefinition current = parameterRepository.findById(currentId).orElse(null);
            currentId = current != null ? current.getParentId() : null;
        }
        return depth;
    }

    /**
     * Validate derived expression constraints:
     * - DERIVED must have non-empty expressionText and valid expressionType
     * - REQUEST must have null expressionText
     */
    private void validateDerivedExpression(String parameterType, String expressionText, String expressionType) {
        if ("DERIVED".equals(parameterType)) {
            if (expressionText == null || expressionText.isBlank()) {
                throw new BusinessException(ErrorCode.PARAMETER_EXPRESSION_REQUIRED,
                        "衍生参数必须提供表达式", HttpStatus.BAD_REQUEST);
            }
            if (expressionType == null || !VALID_EXPRESSION_TYPES.contains(expressionType)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "衍生参数必须指定有效的表达式类型 (JAVASCRIPT 或 EXCEL_FORMULA)",
                        HttpStatus.BAD_REQUEST);
            }
            // Validate expression syntax via ExpressionEngine
            ExpressionType exprType = ExpressionType.valueOf(expressionType);
            ExpressionValidationResult result = expressionEngine.validateExpression(expressionText, exprType);
            if (!result.isValid()) {
                throw new BusinessException(ErrorCode.EXPRESSION_SYNTAX_ERROR,
                        "表达式语法错误: " + result.getErrorMessage(), HttpStatus.BAD_REQUEST);
            }
        } else if ("REQUEST".equals(parameterType)) {
            if (expressionText != null && !expressionText.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "请求参数不能包含表达式", HttpStatus.BAD_REQUEST);
            }
        }
    }

    /**
     * Check for duplicate parameter name within the same scope (templateId + parentId).
     */
    private void checkDuplicateName(Long templateId, Long parentId, String name) {
        boolean exists;
        if (parentId == null) {
            exists = parameterRepository.existsByTemplateIdAndParentIdIsNullAndName(templateId, name);
        } else {
            exists = parameterRepository.existsByTemplateIdAndParentIdAndName(templateId, parentId, name);
        }
        if (exists) {
            throw new BusinessException(ErrorCode.PARAMETER_DUPLICATE_NAME,
                    "同一层级下参数名称已存在: " + name, HttpStatus.CONFLICT);
        }
    }

    /**
     * Validate validation rules: check recognized keys, compatibility with data type,
     * range consistency, and pattern syntax.
     */
    void validateValidationRules(String dataType, Map<String, Object> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : rules.entrySet()) {
            String ruleKey = entry.getKey();

            // custom_message is always allowed
            if ("custom_message".equals(ruleKey)) {
                continue;
            }

            // Check recognized key
            if (!RECOGNIZED_RULE_KEYS.contains(ruleKey)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "不支持的校验规则: " + ruleKey, HttpStatus.BAD_REQUEST);
            }

            // Check compatibility with data type
            Set<String> compatibleTypes = RULE_COMPATIBILITY.get(ruleKey);
            if (compatibleTypes != null && !compatibleTypes.contains(dataType)) {
                throw new BusinessException(ErrorCode.PARAMETER_VALIDATION_RULE_INCOMPATIBLE,
                        "校验规则 '" + ruleKey + "' 与数据类型 '" + dataType + "' 不兼容",
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Range consistency checks
        validateRangeConsistency(rules, "min_length", "max_length");
        validateRangeConsistency(rules, "min", "max");
        validateRangeConsistency(rules, "min_items", "max_items");

        // Date range consistency: date_after must be before date_before
        if (rules.containsKey("date_after") && rules.containsKey("date_before")) {
            Object afterVal = rules.get("date_after");
            Object beforeVal = rules.get("date_before");
            if (afterVal instanceof String afterStr && beforeVal instanceof String beforeStr) {
                if (afterStr.compareTo(beforeStr) >= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "date_after 必须早于 date_before", HttpStatus.BAD_REQUEST);
                }
            }
        }

        // Pattern syntax validation
        if (rules.containsKey("pattern")) {
            Object patternValue = rules.get("pattern");
            if (patternValue instanceof String patternStr) {
                try {
                    Pattern.compile(patternStr);
                } catch (PatternSyntaxException e) {
                    throw new BusinessException(ErrorCode.PARAMETER_INVALID_PATTERN,
                            "正则表达式语法错误: " + e.getDescription(), HttpStatus.BAD_REQUEST);
                }
            }
        }
    }

    /**
     * Validate that a lower bound is <= upper bound for a pair of range rules.
     */
    private void validateRangeConsistency(Map<String, Object> rules, String minKey, String maxKey) {
        if (rules.containsKey(minKey) && rules.containsKey(maxKey)) {
            double minVal = toDouble(rules.get(minKey));
            double maxVal = toDouble(rules.get(maxKey));
            if (minVal > maxVal) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "校验规则范围无效: " + minKey + " (" + minVal + ") 不能大于 " + maxKey + " (" + maxVal + ")",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    /**
     * Detect circular dependencies among DERIVED parameters using topological sort (Kahn's algorithm).
     * <p>
     * Builds a dependency graph from all DERIVED parameters in the template, including the
     * parameter being created/updated (paramName with expressionText). If a cycle is detected,
     * throws BusinessException with PARAMETER_CIRCULAR_DEPENDENCY.
     *
     * @param templateId     the template to check
     * @param expressionText the expression text of the parameter being created/updated
     * @param paramName      the name of the parameter being created/updated
     */
    public void detectCircularDependency(Long templateId, String expressionText, String paramName) {
        List<ParameterDefinition> allParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);

        // Collect all parameter names for reference matching
        Set<String> allParamNames = allParams.stream()
                .map(ParameterDefinition::getName)
                .collect(Collectors.toSet());
        allParamNames.add(paramName);

        // Build dependency graph: paramName -> set of parameter names it depends on
        // Only DERIVED parameters have dependencies (via their expression_text)
        Map<String, Set<String>> dependsOn = new HashMap<>();

        for (ParameterDefinition param : allParams) {
            if ("DERIVED".equals(param.getParameterType()) && param.getExpressionText() != null) {
                String name = param.getName();
                String expr = name.equals(paramName) ? expressionText : param.getExpressionText();
                dependsOn.put(name, extractReferencedNames(expr, allParamNames, name));
            }
        }

        // Include the current parameter being created/updated
        if (!dependsOn.containsKey(paramName) && expressionText != null) {
            dependsOn.put(paramName, extractReferencedNames(expressionText, allParamNames, paramName));
        }

        // Kahn's algorithm for topological sort to detect cycles
        // Build adjacency list and in-degree map for DERIVED parameters only
        Set<String> derivedNames = dependsOn.keySet();
        Map<String, Set<String>> adjacency = new HashMap<>(); // A -> B means A depends on B (edge B -> A)
        Map<String, Integer> inDegree = new HashMap<>();

        for (String name : derivedNames) {
            adjacency.putIfAbsent(name, new HashSet<>());
            inDegree.putIfAbsent(name, 0);
        }

        for (Map.Entry<String, Set<String>> entry : dependsOn.entrySet()) {
            String dependent = entry.getKey();
            for (String dependency : entry.getValue()) {
                // Only track edges between DERIVED parameters (cycles can only occur among them)
                if (derivedNames.contains(dependency)) {
                    adjacency.computeIfAbsent(dependency, k -> new HashSet<>()).add(dependent);
                    inDegree.merge(dependent, 1, Integer::sum);
                }
            }
        }

        // Kahn's: start with nodes having in-degree 0
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int processedCount = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            processedCount++;
            Set<String> neighbors = adjacency.getOrDefault(current, Collections.emptySet());
            for (String neighbor : neighbors) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (processedCount < derivedNames.size()) {
            // Cycle detected — find the cycle participants for the error message
            List<String> cycleParticipants = inDegree.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new BusinessException(ErrorCode.PARAMETER_CIRCULAR_DEPENDENCY,
                    "衍生参数存在循环依赖: " + String.join(" → ", cycleParticipants),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Extract parameter names referenced in an expression text.
     * Looks for identifiers (word characters) that match known parameter names.
     */
    Set<String> extractReferencedNames(String expressionText, Set<String> allParamNames, String selfName) {
        if (expressionText == null || expressionText.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> referenced = new HashSet<>();
        // Match word-boundary identifiers: sequences of [a-zA-Z_][a-zA-Z0-9_-]*
        java.util.regex.Matcher matcher = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b").matcher(expressionText);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (allParamNames.contains(token) && !token.equals(selfName)) {
                referenced.add(token);
            }
        }
        return referenced;
    }

    // ── DTO Conversion ──

    private ParameterDTO toDTO(ParameterDefinition entity) {
        ParameterDTO dto = new ParameterDTO();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setParentId(entity.getParentId());
        dto.setName(entity.getName());
        dto.setParameterType(entity.getParameterType());
        dto.setDataType(entity.getDataType());
        dto.setRequired(entity.isRequired());
        dto.setDefaultValue(entity.getDefaultValue());
        dto.setDescription(entity.getDescription());
        dto.setSortOrder(entity.getSortOrder());
        dto.setExpressionText(entity.getExpressionText());
        dto.setExpressionType(entity.getExpressionType());
        dto.setValidationRules(deserializeValidationRules(entity.getValidationRules()));
        dto.setVersion(entity.getVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    // ── Schema ──

    /**
     * Get the parameter schema for a template.
     * Builds a nested tree structure with "properties" for OBJECT and "items" for ARRAY,
     * consistent with JSON Schema conventions.
     */
    @Transactional(readOnly = true)
    public ParameterSchemaDTO getParameterSchema(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在: " + templateId));

        List<ParameterDefinition> allParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);

        // Only include REQUEST parameters in the schema (DERIVED are computed)
        List<ParameterDefinition> requestParams = allParams.stream()
                .filter(p -> "REQUEST".equals(p.getParameterType()))
                .toList();

        long requiredCount = requestParams.stream().filter(ParameterDefinition::isRequired).count();

        // Build tree: group by parentId
        Map<Long, List<ParameterDefinition>> childrenMap = new LinkedHashMap<>();
        List<ParameterDefinition> roots = new ArrayList<>();
        for (ParameterDefinition p : requestParams) {
            if (p.getParentId() == null) {
                roots.add(p);
            } else {
                childrenMap.computeIfAbsent(p.getParentId(), k -> new ArrayList<>()).add(p);
            }
        }

        List<ParameterSchemaEntry> schemaEntries = roots.stream()
                .map(p -> buildSchemaEntry(p, childrenMap))
                .toList();

        Map<String, Object> sampleBody = buildSampleRequestBody(roots, childrenMap);

        return new ParameterSchemaDTO(
                template.getName(),
                0,
                requestParams.size(),
                (int) requiredCount,
                schemaEntries,
                sampleBody
        );
    }

    private ParameterSchemaEntry buildSchemaEntry(ParameterDefinition param,
                                                   Map<Long, List<ParameterDefinition>> childrenMap) {
        List<ParameterDefinition> children = childrenMap.getOrDefault(param.getId(), List.of());
        Map<String, Object> rules = deserializeValidationRules(param.getValidationRules());

        List<ParameterSchemaEntry> properties = null;
        ParameterSchemaEntry items = null;

        if ("OBJECT".equals(param.getDataType()) && !children.isEmpty()) {
            properties = children.stream()
                    .map(c -> buildSchemaEntry(c, childrenMap))
                    .toList();
        } else if ("ARRAY".equals(param.getDataType()) && !children.isEmpty()) {
            // ARRAY items: build a single schema entry representing the element structure
            // If there's one child, use it directly; if multiple, wrap as OBJECT
            if (children.size() == 1) {
                items = buildSchemaEntry(children.get(0), childrenMap);
            } else {
                // Multiple children → element is an OBJECT with these children as properties
                List<ParameterSchemaEntry> itemProps = children.stream()
                        .map(c -> buildSchemaEntry(c, childrenMap))
                        .toList();
                items = new ParameterSchemaEntry(
                        "element", "OBJECT", false, null, null, null, itemProps, null);
            }
        }

        return new ParameterSchemaEntry(
                param.getName(),
                param.getDataType(),
                param.isRequired(),
                param.getDefaultValue(),
                param.getDescription(),
                rules,
                properties,
                items
        );
    }

    private Map<String, Object> buildSampleRequestBody(List<ParameterDefinition> roots,
                                                        Map<Long, List<ParameterDefinition>> childrenMap) {
        Map<String, Object> sample = new LinkedHashMap<>();
        for (ParameterDefinition p : roots) {
            sample.put(p.getName(), buildSampleValue(p, childrenMap));
        }
        return sample;
    }

    private Object buildSampleValue(ParameterDefinition param,
                                     Map<Long, List<ParameterDefinition>> childrenMap) {
        // Use default_value if available
        if (param.getDefaultValue() != null && !param.getDefaultValue().isBlank()) {
            return convertSampleDefault(param.getDefaultValue(), param.getDataType());
        }

        List<ParameterDefinition> children = childrenMap.getOrDefault(param.getId(), List.of());

        return switch (param.getDataType()) {
            case "STRING" -> "example_" + param.getName();
            case "NUMBER" -> 0;
            case "BOOLEAN" -> false;
            case "DATE" -> "2025-01-01";
            case "OBJECT" -> {
                Map<String, Object> obj = new LinkedHashMap<>();
                for (ParameterDefinition child : children) {
                    obj.put(child.getName(), buildSampleValue(child, childrenMap));
                }
                yield obj;
            }
            case "ARRAY" -> {
                if (children.isEmpty()) {
                    yield List.of();
                }
                // Build one sample element
                if (children.size() == 1) {
                    yield List.of(buildSampleValue(children.get(0), childrenMap));
                }
                Map<String, Object> element = new LinkedHashMap<>();
                for (ParameterDefinition child : children) {
                    element.put(child.getName(), buildSampleValue(child, childrenMap));
                }
                yield List.of(element);
            }
            default -> "example";
        };
    }

    private Object convertSampleDefault(String defaultValue, String dataType) {
        try {
            return switch (dataType) {
                case "NUMBER" -> {
                    if (defaultValue.contains(".")) yield Double.parseDouble(defaultValue);
                    else yield Long.parseLong(defaultValue);
                }
                case "BOOLEAN" -> Boolean.parseBoolean(defaultValue);
                default -> defaultValue;
            };
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ── JSON Serialization Helpers ──

    private String serializeValidationRules(Map<String, Object> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "校验规则序列化失败", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> deserializeValidationRules(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize validation_rules: {}", e.getMessage());
            return null;
        }
    }
}
