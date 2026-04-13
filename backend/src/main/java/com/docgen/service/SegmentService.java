package com.docgen.service;

import com.docgen.dto.CreateSegmentRequest;
import com.docgen.dto.SegmentDTO;
import com.docgen.dto.SegmentQueryRequest;
import com.docgen.dto.TemplateDTO;
import com.docgen.dto.UpdateSegmentRequest;
import com.docgen.entity.Segment;
import com.docgen.entity.SegmentTagMapping;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentTagMappingRepository;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.util.TenantContext;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Service handling Segment CRUD, file upload to MinIO, cloning, and component promotion/demotion.
 */
@Service
public class SegmentService {

    private static final Logger log = LoggerFactory.getLogger(SegmentService.class);

    private final SegmentRepository segmentRepository;
    private final SegmentTagMappingRepository segmentTagMappingRepository;
    private final SegmentVersionRepository segmentVersionRepository;
    private final SegmentVersionService segmentVersionService;
    private final DependencyGraphService dependencyGraphService;
    private final AuditLogService auditLogService;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public SegmentService(SegmentRepository segmentRepository,
                          SegmentTagMappingRepository segmentTagMappingRepository,
                          SegmentVersionRepository segmentVersionRepository,
                          SegmentVersionService segmentVersionService,
                          DependencyGraphService dependencyGraphService,
                          AuditLogService auditLogService,
                          MinioClient minioClient) {
        this.segmentRepository = segmentRepository;
        this.segmentTagMappingRepository = segmentTagMappingRepository;
        this.segmentVersionRepository = segmentVersionRepository;
        this.segmentVersionService = segmentVersionService;
        this.dependencyGraphService = dependencyGraphService;
        this.auditLogService = auditLogService;
        this.minioClient = minioClient;
    }

    // ── Create ──

    /**
     * Create a new segment with file upload to MinIO.
     */
    @Transactional
    public SegmentDTO createSegment(CreateSegmentRequest request, MultipartFile file, Long userId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        String filePath = uploadSegmentFile(file, tenantId);

        Segment segment = new Segment();
        segment.setTenantId(tenantId);
        segment.setName(request.getName());
        segment.setDescription(request.getDescription());
        segment.setFilePath(filePath);
        segment.setComponent(false);
        segment.setSegmentType(request.getSegmentType());
        segment.setCreatedBy(userId);
        segment.setCategoryId(request.getCategoryId());

        Segment saved = segmentRepository.save(segment);

        // Save tag mappings
        saveTagMappings(saved.getId(), request.getTagIds());

        auditLogService.log(tenantId, userId, "SEGMENT_CREATED",
                "SEGMENT", saved.getId(),
                "{\"name\":\"" + saved.getName() + "\"}", null);

        log.info("Segment created: name={}, id={}, tenantId={}", saved.getName(), saved.getId(), tenantId);
        return toDTO(saved);
    }

    // ── Update ──

    /**
     * Update an existing segment. If a new file is uploaded, replaces the file in MinIO
     * and delegates to SegmentVersionService to create a new version.
     */
    @Transactional
    public SegmentDTO updateSegment(Long segmentId, UpdateSegmentRequest request, MultipartFile file, Long userId) {
        Segment segment = findSegmentOrThrow(segmentId);
        Long tenantId = TenantContext.getCurrentTenantId();

        if (request.getName() != null) {
            segment.setName(request.getName());
        }
        if (request.getDescription() != null) {
            segment.setDescription(request.getDescription());
        }
        if (request.getSegmentType() != null) {
            segment.setSegmentType(request.getSegmentType());
        }
        if (request.getCategoryId() != null) {
            segment.setCategoryId(request.getCategoryId());
        }

        // If a new file is uploaded, replace the segment file in MinIO
        if (file != null && !file.isEmpty()) {
            String newFilePath = uploadSegmentFile(file, segment.getTenantId());
            segment.setFilePath(newFilePath);

            // Create a new version for the segment
            segmentVersionService.createVersion(segmentId, newFilePath, userId);
        }

        // Update tag mappings if provided
        if (request.getTagIds() != null) {
            segmentTagMappingRepository.deleteBySegmentId(segmentId);
            saveTagMappings(segmentId, request.getTagIds());
        }

        Segment saved = segmentRepository.save(segment);

        auditLogService.log(tenantId, userId, "SEGMENT_UPDATED",
                "SEGMENT", saved.getId(),
                "{\"name\":\"" + saved.getName() + "\"}", null);

        log.info("Segment updated: id={}", saved.getId());
        return toDTO(saved);
    }

    // ── Delete ──

    /**
     * Delete a segment. Checks reference relationships first — if the segment is referenced
     * by any Composite_Template, deletion is rejected with SEGMENT_REFERENCED (409).
     */
    @Transactional
    public void deleteSegment(Long segmentId, Long userId) {
        Segment segment = findSegmentOrThrow(segmentId);
        Long tenantId = TenantContext.getCurrentTenantId();

        // Check reference relationships — reject deletion if referenced by any Composite_Template
        if (dependencyGraphService.isReferenced(segmentId)) {
            List<TemplateDTO> refs = dependencyGraphService.getReferencingTemplates(segmentId);
            String refNames = refs.stream()
                    .map(TemplateDTO::getName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            throw new BusinessException(ErrorCode.SEGMENT_REFERENCED,
                    "段落被组合模板引用，无法删除: " + refNames, HttpStatus.CONFLICT);
        }

        // Clean up related data
        segmentTagMappingRepository.deleteBySegmentId(segmentId);
        segmentVersionRepository.deleteBySegmentId(segmentId);

        // Delete the file from MinIO (best-effort)
        deleteSegmentFile(segment.getFilePath());

        segmentRepository.delete(segment);

        auditLogService.log(tenantId, userId, "SEGMENT_DELETED",
                "SEGMENT", segmentId,
                "{\"name\":\"" + segment.getName() + "\"}", null);

        log.info("Segment deleted: id={}", segmentId);
    }

    // ── List / Get ──

    /**
     * List segments with pagination and optional filters (name search, tag filter, category, type).
     */
    @Transactional(readOnly = true)
    public Page<SegmentDTO> listSegments(SegmentQueryRequest query, Pageable pageable) {
        Long tenantId = TenantContext.getCurrentTenantId();

        String name = (query != null) ? query.getName() : null;
        if (name != null && name.isBlank()) {
            name = null;
        }
        Long categoryId = (query != null) ? query.getCategoryId() : null;
        String segmentType = (query != null) ? query.getSegmentType() : null;
        if (segmentType != null && segmentType.isBlank()) {
            segmentType = null;
        }
        Boolean isComponent = (query != null) ? query.getIsComponent() : null;
        List<Long> tagIds = (query != null) ? query.getTagIds() : null;

        // If tag filtering is requested, first find segment IDs that match all tags
        List<Long> segmentIds = null;
        if (tagIds != null && !tagIds.isEmpty()) {
            segmentIds = segmentTagMappingRepository.findSegmentIdsHavingAllTags(tagIds, tagIds.size());
            if (segmentIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        return segmentRepository.findByFilters(tenantId, name, categoryId, segmentType, isComponent, segmentIds, pageable)
                .map(this::toDTO);
    }

    /**
     * Get a single segment by ID.
     */
    @Transactional(readOnly = true)
    public SegmentDTO getSegment(Long segmentId) {
        Segment segment = findSegmentOrThrow(segmentId);
        return toDTO(segment);
    }

    // ── Clone ──

    /**
     * Clone a segment: copies the .docx file in MinIO via CopyObject and creates
     * a new segment with "- 副本" name suffix.
     */
    @Transactional
    public SegmentDTO cloneSegment(Long segmentId, Long userId) {
        Segment source = findSegmentOrThrow(segmentId);
        Long tenantId = TenantContext.getCurrentTenantId();

        // Copy the segment file in MinIO
        String clonedFilePath = copySegmentFileInMinio(source.getFilePath(), source.getTenantId());

        Segment clone = new Segment();
        clone.setTenantId(source.getTenantId());
        clone.setName(source.getName() + " - 副本");
        clone.setDescription(source.getDescription());
        clone.setFilePath(clonedFilePath);
        clone.setComponent(false);
        clone.setSegmentType(source.getSegmentType());
        clone.setCreatedBy(userId);
        clone.setCategoryId(source.getCategoryId());

        Segment saved = segmentRepository.save(clone);

        // Copy tag mappings from source
        List<SegmentTagMapping> sourceTags = segmentTagMappingRepository.findBySegmentId(segmentId);
        for (SegmentTagMapping tag : sourceTags) {
            segmentTagMappingRepository.save(new SegmentTagMapping(saved.getId(), tag.getTagId()));
        }

        auditLogService.log(tenantId, userId, "SEGMENT_CLONED",
                "SEGMENT", saved.getId(),
                "{\"sourceId\":" + segmentId + ",\"name\":\"" + saved.getName() + "\"}", null);

        log.info("Segment cloned: sourceId={}, cloneId={}, cloneName={}",
                segmentId, saved.getId(), saved.getName());
        return toDTO(saved);
    }

    // ── Promote / Demote ──

    /**
     * Promote a regular segment to a Component_Template (set is_component = true).
     */
    @Transactional
    public SegmentDTO promoteToComponent(Long segmentId, Long userId) {
        Segment segment = findSegmentOrThrow(segmentId);
        Long tenantId = TenantContext.getCurrentTenantId();

        if (segment.isComponent()) {
            log.debug("Segment {} is already a component", segmentId);
            return toDTO(segment);
        }

        segment.setComponent(true);
        Segment saved = segmentRepository.save(segment);

        auditLogService.log(tenantId, userId, "SEGMENT_PROMOTED",
                "SEGMENT", saved.getId(),
                "{\"name\":\"" + saved.getName() + "\"}", null);

        log.info("Segment promoted to component: id={}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Demote a Component_Template back to a regular segment.
     * Rejected with COMPONENT_REFERENCED (409) if referenced by 2+ Composite_Templates.
     */
    @Transactional
    public SegmentDTO demoteFromComponent(Long segmentId, Long userId) {
        Segment segment = findSegmentOrThrow(segmentId);
        Long tenantId = TenantContext.getCurrentTenantId();

        if (!segment.isComponent()) {
            log.debug("Segment {} is already a regular segment", segmentId);
            return toDTO(segment);
        }

        // Check reference count — reject demotion if referenced by 2+ Composite_Templates
        int refCount = dependencyGraphService.getReferenceCount(segmentId);
        if (refCount >= 2) {
            throw new BusinessException(ErrorCode.COMPONENT_REFERENCED,
                    "组件模板被多个组合模板引用，无法降级", HttpStatus.CONFLICT);
        }

        segment.setComponent(false);
        Segment saved = segmentRepository.save(segment);

        auditLogService.log(tenantId, userId, "SEGMENT_DEMOTED",
                "SEGMENT", saved.getId(),
                "{\"name\":\"" + saved.getName() + "\"}", null);

        log.info("Segment demoted from component: id={}", saved.getId());
        return toDTO(saved);
    }

    // ── Private helpers ──

    Segment findSegmentOrThrow(Long id) {
        return segmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SEGMENT_NOT_FOUND, "段落不存在"));
    }

    private String uploadSegmentFile(MultipartFile file, Long tenantId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "段落文件不能为空", HttpStatus.BAD_REQUEST);
        }

        String objectName = String.format("segments/%d/%s_%s",
                tenantId, UUID.randomUUID(), file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload segment file to MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "段落文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return objectName;
    }

    private String copySegmentFileInMinio(String sourceFilePath, Long tenantId) {
        String destObjectName = String.format("segments/%d/%s_%s",
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
            log.error("Failed to copy segment file in MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "段落文件复制失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return destObjectName;
    }

    private void deleteSegmentFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete segment file from MinIO: {}", e.getMessage(), e);
            // Best-effort deletion — don't fail the transaction
        }
    }

    private String extractFileName(String path) {
        if (path == null) return "segment.docx";
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            String nameWithUuid = path.substring(lastSlash + 1);
            int underscoreIdx = nameWithUuid.indexOf('_');
            if (underscoreIdx > 0 && underscoreIdx < nameWithUuid.length() - 1) {
                return nameWithUuid.substring(underscoreIdx + 1);
            }
            return nameWithUuid;
        }
        return path;
    }

    private void saveTagMappings(Long segmentId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            segmentTagMappingRepository.save(new SegmentTagMapping(segmentId, tagId));
        }
    }

    private SegmentDTO toDTO(Segment segment) {
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
