package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.dto.SegmentVersionDiffResult.SegmentDiffEntry;
import com.docgen.entity.SegmentVersion;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.*;

/**
 * Service for managing segment-level version control.
 * Supports publishing (snapshotting) individual segments and comparing versions.
 */
@Service
public class SegmentVersionService {

    private static final Logger log = LoggerFactory.getLogger(SegmentVersionService.class);

    /** Retries when two publishers read the same max version before either commits (unique constraint). */
    private static final int PUBLISH_MAX_ATTEMPTS = 5;

    private final SegmentVersionRepository segmentVersionRepository;
    private final TemplateRepository templateRepository;
    private final AssemblyConfigService assemblyConfigService;
    private final MinioClient minioClient;
    private final ContentDiffService contentDiffService;
    private final TransactionTemplate publishTransactionTemplate;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public SegmentVersionService(SegmentVersionRepository segmentVersionRepository,
                                 TemplateRepository templateRepository,
                                 AssemblyConfigService assemblyConfigService,
                                 MinioClient minioClient,
                                 ContentDiffService contentDiffService,
                                 PlatformTransactionManager transactionManager) {
        this.segmentVersionRepository = segmentVersionRepository;
        this.templateRepository = templateRepository;
        this.assemblyConfigService = assemblyConfigService;
        this.minioClient = minioClient;
        this.contentDiffService = contentDiffService;
        this.publishTransactionTemplate = new TransactionTemplate(transactionManager);
        this.publishTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Publish (snapshot) a segment: copies the current file in MinIO and records a new version.
     * Uses a short REQUIRES_NEW retry loop so concurrent publishers that pick the same next version
     * do not leave the API as an uncaught {@link DataIntegrityViolationException}.
     */
    public SegmentVersionDTO publishSegment(Long templateId, PublishSegmentRequest request, Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Template template = findCompositeTemplateOrThrow(templateId);

        AssemblyConfigDTO config = assemblyConfigService.deserialize(template.getAssemblyConfig());
        AssemblySegmentEntry segment = findSegmentByName(config, request.getSegmentName());

        if (segment.getFilePath() == null || segment.getFilePath().isBlank()) {
            throw new BusinessException(ErrorCode.SEGMENT_FILE_NOT_FOUND,
                    "Segment file path is empty; cannot publish", HttpStatus.BAD_REQUEST);
        }

        DataIntegrityViolationException lastConflict = null;
        for (int attempt = 1; attempt <= PUBLISH_MAX_ATTEMPTS; attempt++) {
            try {
                return publishTransactionTemplate.execute(status ->
                        publishSegmentSnapshot(tenantId, templateId, request, userId, segment));
            } catch (DataIntegrityViolationException e) {
                lastConflict = e;
                if (!isSegmentVersionUniqueConstraintViolation(e)) {
                    throw e;
                }
                if (attempt >= PUBLISH_MAX_ATTEMPTS) {
                    break;
                }
                log.warn("Concurrent segment publish conflict for templateId={} segment={} (attempt {}/{})",
                        templateId, request.getSegmentName(), attempt, PUBLISH_MAX_ATTEMPTS);
            }
        }
        throw new BusinessException(ErrorCode.SEGMENT_VERSION_PUBLISH_CONFLICT,
                "Could not assign a unique segment version after concurrent publishes; please retry.",
                HttpStatus.CONFLICT, lastConflict);
    }

    /**
     * One publish attempt in its own transaction (allocates version, copies MinIO object, persists row).
     */
    private SegmentVersionDTO publishSegmentSnapshot(Long tenantId,
                                                     Long templateId,
                                                     PublishSegmentRequest request,
                                                     Long userId,
                                                     AssemblySegmentEntry segment) {
        int nextVersion = segmentVersionRepository
                .findMaxVersionNumber(templateId, request.getSegmentName())
                .map(max -> max + 1)
                .orElse(1);

        String versionedPath = copySegmentFile(segment.getFilePath(), templateId, request.getSegmentName(), nextVersion);
        String configSnapshot = buildConfigSnapshot(segment);

        SegmentVersion version = new SegmentVersion();
        version.setTenantId(tenantId);
        version.setTemplateId(templateId);
        version.setSegmentName(request.getSegmentName());
        version.setVersionNumber(nextVersion);
        version.setFilePath(versionedPath);
        version.setSegmentType(segment.getSegmentType());
        version.setConfigSnapshot(configSnapshot);
        version.setComment(request.getComment());
        version.setCreatedBy(userId);

        SegmentVersion saved = segmentVersionRepository.save(version);
        log.info("Segment published: templateId={}, segment={}, version={}",
                templateId, request.getSegmentName(), nextVersion);
        return toDTO(saved);
    }

    /**
     * Visible for tests. Detects unique constraint on (template_id, segment_name, version_number).
     */
    public static boolean isSegmentVersionUniqueConstraintViolation(DataIntegrityViolationException ex) {
        String combined = collectMessages(ex).toLowerCase(Locale.ROOT);
        if (combined.contains("uq_segment_versions_template_name_version")) {
            return true;
        }
        Throwable root = ex.getRootCause();
        if (root instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
            return combined.contains("segment_versions") || combined.contains("uq_segment_versions");
        }
        return false;
    }

    private static String collectMessages(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable t = ex;
        int depth = 0;
        while (t != null && depth++ < 12) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            }
            t = t.getCause();
        }
        return sb.toString();
    }

    /**
     * Get all versions for a specific segment.
     */
    @Transactional(readOnly = true)
    public List<SegmentVersionDTO> getSegmentVersions(Long templateId, String segmentName) {
        findCompositeTemplateOrThrow(templateId);
        return segmentVersionRepository
                .findByTemplateIdAndSegmentNameOrderByVersionNumberDesc(templateId, segmentName)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Compare two versions of a segment.
     */
    @Transactional(readOnly = true)
    public SegmentVersionDiffResult compareSegmentVersions(
            Long templateId, String segmentName, int versionA, int versionB,
            boolean includeContentDiff) {
        SegmentVersion verA = segmentVersionRepository
                .findByTemplateIdAndSegmentNameAndVersionNumber(templateId, segmentName, versionA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VERSION_NOT_FOUND,
                        "片段版本 v" + versionA + " 不存在"));

        SegmentVersion verB = segmentVersionRepository
                .findByTemplateIdAndSegmentNameAndVersionNumber(templateId, segmentName, versionB)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VERSION_NOT_FOUND,
                        "片段版本 v" + versionB + " 不存在"));

        // Short-circuit: same version comparison
        if (versionA == versionB) {
            SegmentVersionDiffResult result = new SegmentVersionDiffResult();
            result.setTemplateId(templateId);
            result.setSegmentName(segmentName);
            result.setVersionA(versionA);
            result.setVersionB(versionB);
            result.setDiffs(new ArrayList<>());
            result.setFilePathChanged(false);
            result.setOldFilePath(verA.getFilePath());
            result.setNewFilePath(verB.getFilePath());
            result.setContentChanged(false);
            return result;
        }

        List<SegmentDiffEntry> diffs = new ArrayList<>();

        // Compare segment type
        diffField(diffs, "segmentType", verA.getSegmentType(), verB.getSegmentType());

        // Compare config snapshots
        diffField(diffs, "configSnapshot", verA.getConfigSnapshot(), verB.getConfigSnapshot());

        // Compare comment
        diffField(diffs, "comment", verA.getComment(), verB.getComment());

        boolean filePathChanged = !Objects.equals(verA.getFilePath(), verB.getFilePath());

        SegmentVersionDiffResult result = new SegmentVersionDiffResult();
        result.setTemplateId(templateId);
        result.setSegmentName(segmentName);
        result.setVersionA(versionA);
        result.setVersionB(versionB);
        result.setDiffs(diffs);
        result.setFilePathChanged(filePathChanged);
        result.setOldFilePath(verA.getFilePath());
        result.setNewFilePath(verB.getFilePath());

        // Content diff: use lightweight check when full diff not requested
        try {
            if (includeContentDiff) {
                ContentDiffResult contentDiff = contentDiffService.computeContentDiff(
                        verA.getFilePath(), verB.getFilePath());
                result.setContentChanged(contentDiff.contentChanged());
                result.setTruncated(contentDiff.truncated());
                result.setContentDiffs(contentDiff.lines());
            } else {
                boolean changed = contentDiffService.hasContentChanged(
                        verA.getFilePath(), verB.getFilePath());
                result.setContentChanged(changed);
            }
        } catch (Exception e) {
            log.warn("Content diff computation failed for segment '{}' versions v{} vs v{}: {}",
                    segmentName, versionA, versionB, e.getMessage());
            result.setContentChanged(false);
            result.setContentDiffs(new ArrayList<>());
            result.setTruncated(false);
        }

        return result;
    }

    /**
     * Rollback a segment to a specific version by copying the versioned file back.
     */
    @Transactional
    public SegmentVersionDTO rollbackSegment(Long templateId, String segmentName, int targetVersion) {
        Template template = findCompositeTemplateOrThrow(templateId);

        SegmentVersion target = segmentVersionRepository
                .findByTemplateIdAndSegmentNameAndVersionNumber(templateId, segmentName, targetVersion)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VERSION_NOT_FOUND,
                        "片段版本 v" + targetVersion + " 不存在"));

        // Update the assembly config to point to the versioned file
        AssemblyConfigDTO config = assemblyConfigService.deserialize(template.getAssemblyConfig());
        AssemblySegmentEntry segment = findSegmentByName(config, segmentName);

        // Copy versioned file back to the segment's current path
        copyFileInMinio(target.getFilePath(), segment.getFilePath());

        log.info("Segment rolled back: templateId={}, segment={}, toVersion={}",
                templateId, segmentName, targetVersion);

        return toDTO(target);
    }


    private Template findCompositeTemplateOrThrow(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));
        if (!"COMPOSITE".equals(template.getTemplateType())) {
            throw new BusinessException(ErrorCode.ASSEMBLY_CONFIG_INVALID,
                    "仅组合模板支持片段版本管理", HttpStatus.BAD_REQUEST);
        }
        return template;
    }

    private AssemblySegmentEntry findSegmentByName(AssemblyConfigDTO config, String segmentName) {
        if (config.getSegments() == null) {
            throw new ResourceNotFoundException(ErrorCode.SEGMENT_FILE_NOT_FOUND, "片段不存在: " + segmentName);
        }
        return config.getSegments().stream()
                .filter(s -> segmentName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SEGMENT_FILE_NOT_FOUND, "片段不存在: " + segmentName));
    }

    private String copySegmentFile(String sourcePath, Long templateId, String segmentName, int version) {
        String safeName = segmentName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String destPath = String.format("segments/%d/versions/%s_v%d.docx", templateId, safeName, version);
        copyFileInMinio(sourcePath, destPath);
        return destPath;
    }

    private void copyFileInMinio(String source, String dest) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(dest)
                    .source(CopySource.builder()
                            .bucket(bucketName)
                            .object(source)
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("Failed to copy file in MinIO: {} -> {}", source, dest, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "片段文件复制失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String buildConfigSnapshot(AssemblySegmentEntry segment) {
        return String.format(
                "{\"segmentType\":\"%s\",\"enabled\":%s,\"pageBreakBefore\":%s,\"conditionExpression\":\"%s\"}",
                segment.getSegmentType() != null ? segment.getSegmentType() : "",
                segment.isEnabled(),
                segment.isPageBreakBefore(),
                segment.getConditionExpression() != null ? segment.getConditionExpression() : ""
        );
    }

    private void diffField(List<SegmentDiffEntry> diffs, String field, String valA, String valB) {
        if (valA == null && valB == null) return;
        if (valA == null) {
            diffs.add(new SegmentDiffEntry(field, "ADDED", null, valB));
        } else if (valB == null) {
            diffs.add(new SegmentDiffEntry(field, "REMOVED", valA, null));
        } else if (!valA.equals(valB)) {
            diffs.add(new SegmentDiffEntry(field, "MODIFIED", valA, valB));
        }
    }

    private SegmentVersionDTO toDTO(SegmentVersion v) {
        return new SegmentVersionDTO(
                v.getId(), v.getTemplateId(), v.getSegmentName(),
                v.getVersionNumber(), v.getFilePath(), v.getSegmentType(),
                v.getConfigSnapshot(), v.getComment(),
                v.getCreatedBy(), v.getCreatedAt()
        );
    }
}
