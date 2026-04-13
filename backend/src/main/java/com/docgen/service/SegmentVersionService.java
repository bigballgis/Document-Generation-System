package com.docgen.service;

import com.docgen.dto.SegmentDTO;
import com.docgen.dto.SegmentVersionDTO;
import com.docgen.dto.VersionDiffResult;
import com.docgen.dto.VersionDiffResult.ChangeSummary;
import com.docgen.dto.VersionDiffResult.DiffEntry;
import com.docgen.dto.VersionDiffResult.DiffEntry.ChangeType;
import com.docgen.entity.Segment;
import com.docgen.entity.SegmentVersion;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.util.TenantContext;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service handling Segment version management: creation, listing, rollback, and comparison.
 */
@Service
public class SegmentVersionService {

    private static final Logger log = LoggerFactory.getLogger(SegmentVersionService.class);

    private final SegmentVersionRepository segmentVersionRepository;
    private final SegmentRepository segmentRepository;
    private final MinioClient minioClient;
    private final AuditLogService auditLogService;
    private final DependencyGraphService dependencyGraphService;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public SegmentVersionService(SegmentVersionRepository segmentVersionRepository,
                                 SegmentRepository segmentRepository,
                                 MinioClient minioClient,
                                 AuditLogService auditLogService,
                                 DependencyGraphService dependencyGraphService) {
        this.segmentVersionRepository = segmentVersionRepository;
        this.segmentRepository = segmentRepository;
        this.minioClient = minioClient;
        this.auditLogService = auditLogService;
        this.dependencyGraphService = dependencyGraphService;
    }

    /**
     * Create a new version for a segment. Version number is strictly incremented.
     * The file is copied to segment-versions/{tenantId}/{segmentId}/{versionNumber}_{uuid}.docx.
     */
    @Transactional
    public SegmentVersionDTO createVersion(Long segmentId, String filePath, Long userId) {
        Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SEGMENT_NOT_FOUND, "段落不存在"));

        Long tenantId = segment.getTenantId();

        int nextVersion = segmentVersionRepository.findMaxVersionNumberBySegmentId(segmentId)
                .orElse(0) + 1;

        String versionFilePath = copyFileToVersionStorage(filePath, tenantId, segmentId, nextVersion);

        SegmentVersion version = new SegmentVersion();
        version.setSegmentId(segmentId);
        version.setVersionNumber(nextVersion);
        version.setFilePath(versionFilePath);
        version.setCreatedBy(userId);

        SegmentVersion saved = segmentVersionRepository.save(version);

        auditLogService.log(tenantId, userId, "SEGMENT_VERSION_CREATED",
                "SEGMENT_VERSION", saved.getId(),
                "{\"segmentId\":" + segmentId + ",\"versionNumber\":" + nextVersion + "}", null);

        log.info("Segment version created: segmentId={}, versionNumber={}, id={}",
                segmentId, nextVersion, saved.getId());

        // Notify referencing Composite_Templates if this is a Component_Template
        if (segment.isComponent()) {
            dependencyGraphService.notifyComponentChange(segmentId, nextVersion);
        }

        return toDTO(saved);
    }

    /**
     * List versions for a segment, ordered by version number descending (newest first).
     */
    @Transactional(readOnly = true)
    public Page<SegmentVersionDTO> listVersions(Long segmentId, Pageable pageable) {
        // Verify segment exists
        if (!segmentRepository.existsById(segmentId)) {
            throw new ResourceNotFoundException(ErrorCode.SEGMENT_NOT_FOUND, "段落不存在");
        }

        return segmentVersionRepository.findBySegmentIdOrderByVersionNumberDesc(segmentId, pageable)
                .map(this::toDTO);
    }

    /**
     * Rollback a segment to a specified historical version.
     * Creates a new version (version number continues incrementing) based on the historical version's file.
     * Also updates the segment's current file path.
     */
    @Transactional
    public SegmentDTO rollbackToVersion(Long segmentId, Long versionId, Long userId) {
        Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SEGMENT_NOT_FOUND, "段落不存在"));

        SegmentVersion targetVersion = segmentVersionRepository.findByIdAndSegmentId(versionId, segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VERSION_NOT_FOUND, "段落版本不存在"));

        Long tenantId = segment.getTenantId();

        // Create a new version based on the historical version's file
        int nextVersion = segmentVersionRepository.findMaxVersionNumberBySegmentId(segmentId)
                .orElse(0) + 1;

        String newFilePath = copyFileToVersionStorage(
                targetVersion.getFilePath(), tenantId, segmentId, nextVersion);

        SegmentVersion newVersion = new SegmentVersion();
        newVersion.setSegmentId(segmentId);
        newVersion.setVersionNumber(nextVersion);
        newVersion.setFilePath(newFilePath);
        newVersion.setCreatedBy(userId);

        segmentVersionRepository.save(newVersion);

        // Update the segment's current file path to the new version's file
        String currentFilePath = copyFileToSegmentStorage(targetVersion.getFilePath(), tenantId);
        segment.setFilePath(currentFilePath);
        Segment saved = segmentRepository.save(segment);

        auditLogService.log(tenantId, userId, "SEGMENT_VERSION_ROLLBACK",
                "SEGMENT_VERSION", newVersion.getId(),
                "{\"segmentId\":" + segmentId
                        + ",\"fromVersion\":" + targetVersion.getVersionNumber()
                        + ",\"newVersion\":" + nextVersion + "}", null);

        log.info("Segment rolled back: segmentId={}, fromVersion={}, newVersion={}",
                segmentId, targetVersion.getVersionNumber(), nextVersion);

        return toSegmentDTO(saved);
    }

    /**
     * Compare two versions of a segment by version numbers.
     * Detects file path differences between the two versions.
     */
    @Transactional(readOnly = true)
    public VersionDiffResult compareVersions(Long segmentId, int versionA, int versionB) {
        SegmentVersion verA = segmentVersionRepository.findBySegmentIdAndVersionNumber(segmentId, versionA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VERSION_NOT_FOUND,
                        "段落版本 " + versionA + " 不存在"));

        SegmentVersion verB = segmentVersionRepository.findBySegmentIdAndVersionNumber(segmentId, versionB)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_VERSION_NOT_FOUND,
                        "段落版本 " + versionB + " 不存在"));

        List<DiffEntry> diffs = new ArrayList<>();

        // Compare file paths
        if (!verA.getFilePath().equals(verB.getFilePath())) {
            diffs.add(new DiffEntry(ChangeType.MODIFIED, "filePath",
                    verA.getFilePath(), verB.getFilePath()));
        }

        // Compare created by
        if (!verA.getCreatedBy().equals(verB.getCreatedBy())) {
            diffs.add(new DiffEntry(ChangeType.MODIFIED, "createdBy",
                    String.valueOf(verA.getCreatedBy()), String.valueOf(verB.getCreatedBy())));
        }

        // Compare timestamps
        if (verA.getCreatedAt() != null && verB.getCreatedAt() != null
                && !verA.getCreatedAt().equals(verB.getCreatedAt())) {
            diffs.add(new DiffEntry(ChangeType.MODIFIED, "createdAt",
                    verA.getCreatedAt().toString(), verB.getCreatedAt().toString()));
        }

        int additions = 0, deletions = 0, modifications = 0;
        for (DiffEntry entry : diffs) {
            switch (entry.getChangeType()) {
                case ADDED -> additions++;
                case REMOVED -> deletions++;
                case MODIFIED -> modifications++;
            }
        }

        VersionDiffResult result = new VersionDiffResult();
        result.setVersionA(versionA);
        result.setVersionB(versionB);
        result.setTextDiffs(diffs);
        result.setVariableDiffs(List.of());
        result.setDataSourceDiffs(List.of());
        result.setExpressionDiffs(List.of());
        result.setDifferences(diffs.stream()
                .map(d -> d.getField() + ": " + d.getOldValue() + " → " + d.getNewValue())
                .toList());
        result.setChangeSummary(new ChangeSummary(
                additions + deletions + modifications,
                additions, deletions, modifications));

        log.info("Segment version diff completed: segmentId={}, v{}→v{}, changes={}",
                segmentId, versionA, versionB, result.getChangeSummary().getTotalChanges());

        return result;
    }

    // ── Private helpers ──

    private String copyFileToVersionStorage(String sourceFilePath, Long tenantId,
                                            Long segmentId, int versionNumber) {
        String destObjectName = String.format("segment-versions/%d/%d/%d_%s.docx",
                tenantId, segmentId, versionNumber, UUID.randomUUID());

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
            log.error("Failed to copy segment file to version storage: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "段落版本文件复制失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return destObjectName;
    }

    private String copyFileToSegmentStorage(String sourceFilePath, Long tenantId) {
        String destObjectName = String.format("segments/%d/%s_segment.docx",
                tenantId, UUID.randomUUID());

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
            log.error("Failed to copy version file to segment storage: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "段落文件恢复失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return destObjectName;
    }

    private SegmentVersionDTO toDTO(SegmentVersion version) {
        SegmentVersionDTO dto = new SegmentVersionDTO();
        dto.setId(version.getId());
        dto.setSegmentId(version.getSegmentId());
        dto.setVersionNumber(version.getVersionNumber());
        dto.setFilePath(version.getFilePath());
        dto.setCreatedBy(version.getCreatedBy());
        dto.setCreatedAt(version.getCreatedAt());
        return dto;
    }

    private SegmentDTO toSegmentDTO(Segment segment) {
        SegmentDTO dto = new SegmentDTO();
        dto.setId(segment.getId());
        dto.setName(segment.getName());
        dto.setDescription(segment.getDescription());
        dto.setFilePath(segment.getFilePath());
        dto.setComponent(segment.isComponent());
        dto.setSegmentType(segment.getSegmentType());
        dto.setCreatedBy(segment.getCreatedBy());
        dto.setCategoryId(segment.getCategoryId());
        dto.setTenantId(segment.getTenantId());
        dto.setCreatedAt(segment.getCreatedAt());
        dto.setUpdatedAt(segment.getUpdatedAt());
        return dto;
    }
}
