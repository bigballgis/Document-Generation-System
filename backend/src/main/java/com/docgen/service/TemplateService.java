package com.docgen.service;

import com.docgen.dto.CreateTemplateRequest;
import com.docgen.dto.ReviewerCandidateDTO;
import com.docgen.dto.TemplateDTO;
import com.docgen.dto.TemplateQueryRequest;
import com.docgen.dto.TemplateVersionDTO;
import com.docgen.dto.UpdateTemplateRequest;
import com.docgen.entity.Team;
import com.docgen.entity.TeamApprovalMode;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.entity.User;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TeamRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateVersionRepository;
import com.docgen.repository.UserRepository;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service handling template CRUD, file upload to MinIO, and template cloning.
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateTagMappingRepository tagMappingRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public TemplateService(TemplateRepository templateRepository,
                           TemplateVersionRepository templateVersionRepository,
                           TemplateTagMappingRepository tagMappingRepository,
                           UserRepository userRepository,
                           TeamRepository teamRepository,
                           MinioClient minioClient) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.tagMappingRepository = tagMappingRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.minioClient = minioClient;
    }

    /**
     * Create a new template with optional file upload to MinIO.
     */
    @Transactional
    public TemplateDTO createTemplate(CreateTemplateRequest request, MultipartFile file, Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        String filePath = uploadTemplateFile(file, tenantId);
        if (filePath == null) {
            // No file uploaded — create an empty .docx template automatically
            filePath = createEmptyDocxTemplate(tenantId, request.getName());
        }

        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setTemplateFilePath(filePath);
        template.setCreatedBy(userId);
        template.setStatus("DRAFT");

        if (request.getOutputFormat() != null) {
            template.setOutputFormat(request.getOutputFormat());
        }
        if (request.getStorageStrategy() != null) {
            template.setStorageStrategy(request.getStorageStrategy());
        }
        template.setAsync(request.isAsync());
        template.setCategoryId(request.getCategoryId());
        template.setReviewRequired(request.isReviewRequired());

        Long effectiveTeamId = resolveTeamIdForCreate(request.getTeamId(), userId, tenantId);
        template.setTeamId(effectiveTeamId);

        Template saved = templateRepository.save(template);
        log.info("Template created: name={}, id={}, tenantId={}", saved.getName(), saved.getId(), tenantId);
        return toDTO(saved);
    }

    /**
     * List templates with optional keyword search, category filter, and tag filter.
     */
    @Transactional(readOnly = true)
    public Page<TemplateDTO> listTemplates(TemplateQueryRequest query, Pageable pageable) {
        String keyword = (query != null) ? query.getKeyword() : null;
        if (keyword != null && keyword.isBlank()) {
            keyword = null;
        }

        Long categoryId = (query != null) ? query.getCategoryId() : null;
        java.util.List<Long> tagIds = (query != null) ? query.getTagIds() : null;

        // If tag filtering is requested, find template IDs that have all specified tags
        if (tagIds != null && !tagIds.isEmpty()) {
            java.util.List<Long> templateIds = tagMappingRepository.findTemplateIdsHavingAllTags(tagIds, tagIds.size());
            if (templateIds.isEmpty()) {
                return Page.empty(pageable);
            }
            return templateRepository.searchByKeywordAndCategoryAndIds(keyword, categoryId, templateIds, pageable)
                    .map(this::toDTO);
        }

        // Category-only or keyword-only filtering
        if (categoryId != null) {
            return templateRepository.searchByKeywordAndCategory(keyword, categoryId, pageable)
                    .map(this::toDTO);
        }

        return templateRepository.searchByKeyword(keyword, pageable).map(this::toDTO);
    }

    /**
     * Get a single template by ID.
     */
    @Transactional(readOnly = true)
    public TemplateDTO getTemplate(Long templateId) {
        return toDTO(findTemplateOrThrow(templateId));
    }

    /**
     * Users in the same tenant and team as the template who may be selected as reviewers (excludes the template author).
     * For maker-checker teams, filters by {@code reviewLevel}: level 1 → makers, level 2+ → checkers.
     */
    @Transactional(readOnly = true)
    public List<ReviewerCandidateDTO> listReviewerCandidates(Long templateId, Integer reviewLevel) {
        Template template = findTemplateOrThrow(templateId);
        Long tenantId = TenantContext.getCurrentTenantId();
        if (!template.getTenantId().equals(tenantId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "Template not accessible in current tenant context", HttpStatus.FORBIDDEN);
        }
        if (template.getTeamId() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Template must be assigned to a team before listing reviewer candidates",
                    HttpStatus.BAD_REQUEST);
        }
        Team team = teamRepository.findById(template.getTeamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Template team not found", HttpStatus.BAD_REQUEST));
        List<User> users = userRepository.findByTenantIdAndTeamIdOrderByUsernameAsc(tenantId, template.getTeamId());
        Long createdBy = template.getCreatedBy();
        int level = reviewLevel != null ? reviewLevel : 1;
        return users.stream()
                .filter(u -> u.getId() != null && (createdBy == null || !u.getId().equals(createdBy)))
                .filter(u -> includeUserForReviewCandidate(team, level, u))
                .map(u -> new ReviewerCandidateDTO(
                        u.getId(), u.getUsername(), u.getEmail(), u.getTeamId(), u.getRole(), u.getTeamReviewLane()))
                .toList();
    }

    private static boolean includeUserForReviewCandidate(Team team, int reviewLevel, User user) {
        if (team.getApprovalMode() != TeamApprovalMode.MAKER_CHECKER) {
            return true;
        }
        if (reviewLevel <= 1) {
            return "MAKER".equals(user.getTeamReviewLane());
        }
        return "CHECKER".equals(user.getTeamReviewLane());
    }

    /**
     * Update an existing template. Automatically creates a new version snapshot.
     */
    @Transactional
    public TemplateDTO updateTemplate(Long templateId, UpdateTemplateRequest request, MultipartFile file) {
        Template template = findTemplateOrThrow(templateId);

        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getOutputFormat() != null) {
            template.setOutputFormat(request.getOutputFormat());
        }
        if (request.getStorageStrategy() != null) {
            template.setStorageStrategy(request.getStorageStrategy());
        }
        if (request.getAsync() != null) {
            template.setAsync(request.getAsync());
        }
        if (request.getTeamId() != null) {
            assertTeamBelongsToTenant(request.getTeamId(), template.getTenantId());
            template.setTeamId(request.getTeamId());
        }
        if (request.getCategoryId() != null) {
            template.setCategoryId(request.getCategoryId());
        }
        if (request.getReviewRequired() != null) {
            template.setReviewRequired(request.getReviewRequired());
        }

        // If a new file is uploaded, replace the template file in MinIO
        if (file != null && !file.isEmpty()) {
            String newFilePath = uploadTemplateFile(file, template.getTenantId());
            template.setTemplateFilePath(newFilePath);
        }

        Template saved = templateRepository.save(template);

        // Auto-create a new version snapshot
        createVersionSnapshot(saved);

        log.info("Template updated: id={}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Delete a template by ID.
     */
    @Transactional
    public void deleteTemplate(Long templateId) {
        Template template = findTemplateOrThrow(templateId);
        templateRepository.delete(template);
        log.info("Template deleted: id={}", templateId);
    }

    /**
     * Clone a template: creates a full copy with "- 副本" suffix and DRAFT status.
     * Copies the template file in MinIO and duplicates all metadata.
     */
    @Transactional
    public TemplateDTO cloneTemplate(Long templateId) {
        Template source = findTemplateOrThrow(templateId);

        // Copy the template file in MinIO
        String clonedFilePath = copyTemplateFileInMinio(source.getTemplateFilePath(), source.getTenantId());

        Template clone = new Template();
        clone.setTenantId(source.getTenantId());
        clone.setName(source.getName() + " - 副本");
        clone.setDescription(source.getDescription());
        clone.setTemplateFilePath(clonedFilePath);
        clone.setOutputFormat(source.getOutputFormat());
        clone.setStorageStrategy(source.getStorageStrategy());
        clone.setAsync(source.isAsync());
        clone.setTeamId(source.getTeamId());
        clone.setCreatedBy(source.getCreatedBy());
        clone.setCategoryId(source.getCategoryId());
        clone.setReviewRequired(source.isReviewRequired());
        clone.setStatus("DRAFT");
        clone.setTemplateType(source.getTemplateType());
        clone.setAssemblyConfig(source.getAssemblyConfig());
        clone.setRenderConfig(source.getRenderConfig());

        Template saved = templateRepository.save(clone);
        log.info("Template cloned: sourceId={}, cloneId={}, cloneName={}",
                templateId, saved.getId(), saved.getName());
        return toDTO(saved);
    }


    /**
     * Get all versions for a template, ordered by version number descending.
     */
    @Transactional(readOnly = true)
    public List<TemplateVersionDTO> getTemplateVersions(Long templateId) {
        findTemplateOrThrow(templateId);
        return templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)
                .stream()
                .map(this::toVersionDTO)
                .toList();
    }

    /**
     * Rollback a template to a specific version.
     * Creates a NEW version based on the historical version's config; version number continues to increment.
     */
    @Transactional
    public TemplateDTO rollbackToVersion(Long templateId, Long versionId) {
        Template template = findTemplateOrThrow(templateId);

        TemplateVersion targetVersion = templateVersionRepository.findByIdAndTemplateId(versionId, templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VERSION_NOT_FOUND, "模板版本不存在"));

        // Restore template fields from the target version's config
        template.setTemplateFilePath(targetVersion.getTemplateFilePath());
        // configJson stores the full metadata; apply it to the template
        applyConfigJson(template, targetVersion.getConfigJson());

        Template saved = templateRepository.save(template);

        // Create a new version (rollback creates a new version, version number keeps incrementing)
        createVersionSnapshot(saved);

        log.info("Template rolled back: id={}, targetVersionId={}, targetVersionNumber={}",
                templateId, versionId, targetVersion.getVersionNumber());
        return toDTO(saved);
    }

    /**
     * Create a new draft version from an ACTIVE template.
     * This is a special operation that bypasses the state machine,
     * allowing ACTIVE → DRAFT transition for editing purposes.
     */
    @Transactional
    public TemplateDTO createDraftVersion(Long templateId, Long userId) {
        Template template = findTemplateOrThrow(templateId);
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.TEMPLATE_INVALID_STATE_TRANSITION,
                    "只有 ACTIVE 状态的模板才能创建草稿版本", HttpStatus.BAD_REQUEST);
        }
        createVersionSnapshot(template);
        template.setStatus("DRAFT");
        templateRepository.save(template);
        log.info("Draft version created for template: id={}, by userId={}", templateId, userId);
        return toDTO(template);
    }


    private Template findTemplateOrThrow(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));
    }

    private void assertTeamBelongsToTenant(Long teamId, Long tenantId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_FAILED, "Team not found", HttpStatus.BAD_REQUEST));
        if (!team.getTenantId().equals(tenantId)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "Team does not belong to the current tenant", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * If request does not set a team, inherit the creator's team when present; otherwise null (draft may stay unassigned).
     */
    private Long resolveTeamIdForCreate(Long requestTeamId, Long userId, Long tenantId) {
        if (requestTeamId != null) {
            assertTeamBelongsToTenant(requestTeamId, tenantId);
            return requestTeamId;
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || userOpt.get().getTeamId() == null) {
            return null;
        }
        Long fromUser = userOpt.get().getTeamId();
        assertTeamBelongsToTenant(fromUser, tenantId);
        return fromUser;
    }

    private String uploadTemplateFile(MultipartFile file, Long tenantId) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String objectName = String.format("templates/%d/%s_%s",
                tenantId, UUID.randomUUID(), file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload template file to MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "模板文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return objectName;
    }

    /**
     * Create a minimal empty .docx file and upload it to MinIO.
     * A .docx is a ZIP archive with specific XML structure (Open XML).
     */
    private String createEmptyDocxTemplate(Long tenantId, String templateName) {
        String safeName = (templateName != null ? templateName.replaceAll("[^a-zA-Z0-9_\\-]", "_") : "template");
        String objectName = String.format("templates/%d/%s_%s.docx", tenantId, UUID.randomUUID(), safeName);

        try {
            byte[] docxBytes = generateEmptyDocx();
            try (InputStream is = new ByteArrayInputStream(docxBytes)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(is, docxBytes.length, -1)
                        .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .build());
            }
            log.info("Created empty docx template: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to create empty docx template: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "创建空模板文件失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        return objectName;
    }

    /**
     * Generate a minimal valid .docx file (Open XML format).
     */
    private byte[] generateEmptyDocx() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>");

            // _rels/.rels
            addZipEntry(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");

            // word/document.xml — empty document body
            addZipEntry(zos, "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                    + "<w:body><w:p><w:r><w:t></w:t></w:r></w:p></w:body>"
                    + "</w:document>");
        }
        return baos.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String copyTemplateFileInMinio(String sourceFilePath, Long tenantId) {
        String destObjectName = String.format("templates/%d/%s_%s",
                tenantId, UUID.randomUUID(), extractFileName(sourceFilePath));

        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(destObjectName)
                    .source(CopySource.builder()
                            .bucket(bucketName)
                            .object(sourceFilePath)
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("Failed to copy template file in MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "模板文件复制失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return destObjectName;
    }

    private String extractFileName(String path) {
        if (path == null) return "template.docx";
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            // Strip the UUID prefix if present (format: uuid_filename)
            String nameWithUuid = path.substring(lastSlash + 1);
            int underscoreIdx = nameWithUuid.indexOf('_');
            if (underscoreIdx > 0 && underscoreIdx < nameWithUuid.length() - 1) {
                return nameWithUuid.substring(underscoreIdx + 1);
            }
            return nameWithUuid;
        }
        return path;
    }

    private TemplateDTO toDTO(Template template) {
        TemplateDTO dto = new TemplateDTO(
                template.getId(),
                template.getTenantId(),
                template.getName(),
                template.getDescription(),
                template.getTemplateFilePath(),
                template.getOutputFormat(),
                template.getStorageStrategy(),
                template.isAsync(),
                template.getTeamId(),
                template.getCreatedBy(),
                template.getCategoryId(),
                template.isReviewRequired(),
                template.getStatus(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
        dto.setTemplateType(template.getTemplateType());
        dto.setRenderConfig(template.getRenderConfig());
        dto.setVersion(templateVersionRepository.findMaxVersionNumber(template.getId()).orElse(0));
        return dto;
    }

    private TemplateVersionDTO toVersionDTO(TemplateVersion version) {
        return new TemplateVersionDTO(
                version.getId(),
                version.getTemplateId(),
                version.getVersionNumber(),
                version.getTemplateFilePath(),
                version.getConfigJson(),
                version.getCreatedBy(),
                version.getCreatedAt()
        );
    }

    /**
     * Create a version snapshot of the current template state.
     * Version number is strictly monotonically increasing per template.
     */
    private void createVersionSnapshot(Template template) {
        int nextVersion = templateVersionRepository.findMaxVersionNumber(template.getId())
                .map(max -> max + 1)
                .orElse(1);

        TemplateVersion version = new TemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(nextVersion);
        version.setTemplateFilePath(template.getTemplateFilePath());
        version.setConfigJson(buildConfigJson(template));
        version.setCreatedBy(template.getCreatedBy());

        templateVersionRepository.save(version);
        log.info("Template version created: templateId={}, versionNumber={}", template.getId(), nextVersion);
    }

    /**
     * Build a JSON string representing the full template configuration.
     */
    private String buildConfigJson(Template template) {
        return String.format(
                "{\"name\":\"%s\",\"description\":\"%s\",\"outputFormat\":\"%s\","
                        + "\"storageStrategy\":\"%s\",\"async\":%s,\"teamId\":%s,"
                        + "\"categoryId\":%s,\"reviewRequired\":%s,\"status\":\"%s\"}",
                escapeJson(template.getName()),
                escapeJson(template.getDescription()),
                escapeJson(template.getOutputFormat()),
                escapeJson(template.getStorageStrategy()),
                template.isAsync(),
                template.getTeamId(),
                template.getCategoryId(),
                template.isReviewRequired(),
                escapeJson(template.getStatus())
        );
    }

    /**
     * Apply config from a JSON string back to a template entity.
     * Uses simple parsing to avoid external JSON library dependency.
     */
    private void applyConfigJson(Template template, String configJson) {
        template.setName(extractJsonString(configJson, "name"));
        String description = extractJsonString(configJson, "description");
        if (description != null) {
            template.setDescription(description);
        }
        template.setOutputFormat(extractJsonString(configJson, "outputFormat"));
        template.setStorageStrategy(extractJsonString(configJson, "storageStrategy"));
        template.setAsync(Boolean.parseBoolean(extractJsonValue(configJson, "async")));
        String teamId = extractJsonValue(configJson, "teamId");
        template.setTeamId("null".equals(teamId) || teamId == null ? null : Long.parseLong(teamId));
        String categoryId = extractJsonValue(configJson, "categoryId");
        template.setCategoryId("null".equals(categoryId) || categoryId == null ? null : Long.parseLong(categoryId));
        template.setReviewRequired(Boolean.parseBoolean(extractJsonValue(configJson, "reviewRequired")));
        template.setStatus(extractJsonString(configJson, "status"));
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return unescapeJson(json.substring(start, end));
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf(",", start);
        if (end < 0) {
            end = json.indexOf("}", start);
        }
        if (end < 0) return null;
        return json.substring(start, end).trim();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String value) {
        if (value == null) return null;
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}

