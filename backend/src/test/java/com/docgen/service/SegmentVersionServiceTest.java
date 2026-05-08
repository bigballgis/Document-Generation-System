package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.PublishSegmentRequest;
import com.docgen.dto.SegmentVersionDTO;
import com.docgen.entity.SegmentVersion;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SegmentVersionService} publish concurrency handling (WS-04-T06).
 */
@ExtendWith(MockitoExtension.class)
class SegmentVersionServiceTest {

    private static final long TEMPLATE_ID = 10L;
    private static final long TENANT_ID = 1L;

    @Mock
    private SegmentVersionRepository segmentVersionRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private AssemblyConfigService assemblyConfigService;
    @Mock
    private MinioClient minioClient;
    @Mock
    private ContentDiffService contentDiffService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SegmentVersionService service;

    @BeforeEach
    void setUp() throws Exception {
        TransactionStatus status = new SimpleTransactionStatus(true);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        lenient().doNothing().when(transactionManager).commit(any(TransactionStatus.class));
        lenient().doNothing().when(transactionManager).rollback(any(TransactionStatus.class));

        service = new SegmentVersionService(
                segmentVersionRepository,
                templateRepository,
                assemblyConfigService,
                minioClient,
                contentDiffService,
                transactionManager);
        Field bucket = SegmentVersionService.class.getDeclaredField("bucketName");
        bucket.setAccessible(true);
        bucket.set(service, "docgen-test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void isSegmentVersionUniqueConstraintViolation_detectsNamedConstraintMessage() {
        var ex = new DataIntegrityViolationException(
                "could not execute statement; SQL [n/a]; constraint [uq_segment_versions_template_name_version]",
                null);
        assertTrue(SegmentVersionService.isSegmentVersionUniqueConstraintViolation(ex));
    }

    @Test
    void isSegmentVersionUniqueConstraintViolation_detectsPostgres23505WithConstraintName() {
        SQLException sql = new SQLException(
                "duplicate key value violates unique constraint \"uq_segment_versions_template_name_version\"",
                "23505");
        var ex = new DataIntegrityViolationException("could not execute batch", sql);
        assertTrue(SegmentVersionService.isSegmentVersionUniqueConstraintViolation(ex));
    }

    @Test
    void isSegmentVersionUniqueConstraintViolation_falseForUnrelatedViolation() {
        var ex = new DataIntegrityViolationException("other_constraint", null);
        assertFalse(SegmentVersionService.isSegmentVersionUniqueConstraintViolation(ex));
    }

    @Test
    void publishSegment_firstVersion_allocatesVersionOne() throws Exception {
        TenantContext.setCurrentTenantId(TENANT_ID);
        stubCompositeTemplateAndSegment();

        when(segmentVersionRepository.findMaxVersionNumber(eq(TEMPLATE_ID), eq("intro")))
                .thenReturn(Optional.empty());
        SegmentVersion persisted = persistedVersion(1);
        when(segmentVersionRepository.save(any(SegmentVersion.class))).thenReturn(persisted);

        PublishSegmentRequest req = new PublishSegmentRequest();
        req.setSegmentName("intro");
        SegmentVersionDTO dto = service.publishSegment(TEMPLATE_ID, req, 99L);

        assertEquals(1, dto.versionNumber());
        verify(segmentVersionRepository).save(any(SegmentVersion.class));
        verify(minioClient).copyObject(any());
    }

    @Test
    void publishSegment_retriesOnUniqueConstraintThenSucceeds() throws Exception {
        TenantContext.setCurrentTenantId(TENANT_ID);
        stubCompositeTemplateAndSegment();

        when(segmentVersionRepository.findMaxVersionNumber(eq(TEMPLATE_ID), eq("intro")))
                .thenReturn(Optional.of(1))
                .thenReturn(Optional.of(2));

        DataIntegrityViolationException conflict = new DataIntegrityViolationException(
                "constraint [uq_segment_versions_template_name_version]", null);
        SegmentVersion persisted = persistedVersion(3);
        when(segmentVersionRepository.save(any(SegmentVersion.class)))
                .thenThrow(conflict)
                .thenReturn(persisted);

        PublishSegmentRequest req = new PublishSegmentRequest();
        req.setSegmentName("intro");
        SegmentVersionDTO dto = service.publishSegment(TEMPLATE_ID, req, 99L);

        assertEquals(3, dto.versionNumber());
        verify(segmentVersionRepository, times(2)).save(any(SegmentVersion.class));
    }

    @Test
    void publishSegment_nonVersionUniqueViolationPropagates() throws Exception {
        TenantContext.setCurrentTenantId(TENANT_ID);
        stubCompositeTemplateAndSegment();

        when(segmentVersionRepository.findMaxVersionNumber(eq(TEMPLATE_ID), eq("intro")))
                .thenReturn(Optional.empty());
        when(segmentVersionRepository.save(any(SegmentVersion.class)))
                .thenThrow(new DataIntegrityViolationException("unrelated_constraint_xyz", null));

        PublishSegmentRequest req = new PublishSegmentRequest();
        req.setSegmentName("intro");
        assertThrows(DataIntegrityViolationException.class,
                () -> service.publishSegment(TEMPLATE_ID, req, 99L));
    }

    @Test
    void publishSegment_exhaustsRetries_throwsBusinessConflict() throws Exception {
        TenantContext.setCurrentTenantId(TENANT_ID);
        stubCompositeTemplateAndSegment();

        when(segmentVersionRepository.findMaxVersionNumber(eq(TEMPLATE_ID), eq("intro")))
                .thenReturn(Optional.of(0));
        DataIntegrityViolationException conflict = new DataIntegrityViolationException(
                "uq_segment_versions_template_name_version", null);
        when(segmentVersionRepository.save(any(SegmentVersion.class))).thenThrow(conflict);

        PublishSegmentRequest req = new PublishSegmentRequest();
        req.setSegmentName("intro");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publishSegment(TEMPLATE_ID, req, 99L));

        assertEquals(ErrorCode.SEGMENT_VERSION_PUBLISH_CONFLICT, ex.getErrorCode());
        verify(segmentVersionRepository, times(5)).save(any(SegmentVersion.class));
    }

    private void stubCompositeTemplateAndSegment() throws Exception {
        Template template = new Template();
        template.setId(TEMPLATE_ID);
        template.setTemplateType("COMPOSITE");
        template.setAssemblyConfig("{}");
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        AssemblySegmentEntry seg = new AssemblySegmentEntry();
        seg.setName("intro");
        seg.setFilePath("segments/10/intro.docx");
        seg.setSegmentType("BODY");
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(List.of(seg));
        when(assemblyConfigService.deserialize(any())).thenReturn(config);

        lenient().when(minioClient.copyObject(any())).thenReturn(mock(ObjectWriteResponse.class));
    }

    private static SegmentVersion persistedVersion(int versionNumber) {
        SegmentVersion v = new SegmentVersion();
        v.setId(100L + versionNumber);
        v.setTenantId(TENANT_ID);
        v.setTemplateId(TEMPLATE_ID);
        v.setSegmentName("intro");
        v.setVersionNumber(versionNumber);
        v.setFilePath("segments/10/versions/intro_v" + versionNumber + ".docx");
        v.setSegmentType("BODY");
        v.setConfigSnapshot("{}");
        v.setComment(null);
        v.setCreatedBy(99L);
        v.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return v;
    }
}
