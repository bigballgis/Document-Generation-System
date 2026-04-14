package com.docgen.service;

import com.docgen.dto.CreateSegmentRequest;
import com.docgen.dto.SegmentDTO;
import com.docgen.entity.Segment;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentTagMappingRepository;
import com.docgen.repository.SegmentVersionRepository;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SegmentServiceTest {

    @Mock
    private SegmentRepository segmentRepository;

    @Mock
    private SegmentTagMappingRepository segmentTagMappingRepository;

    @Mock
    private SegmentVersionRepository segmentVersionRepository;

    @Mock
    private SegmentVersionService segmentVersionService;

    @Mock
    private DependencyGraphService dependencyGraphService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private MinioClient minioClient;

    private SegmentService segmentService;

    @BeforeEach
    void setUp() throws Exception {
        segmentService = new SegmentService(
                segmentRepository,
                segmentTagMappingRepository,
                segmentVersionRepository,
                segmentVersionService,
                dependencyGraphService,
                auditLogService,
                minioClient
        );
        Field bucketField = SegmentService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(segmentService, "docgen-test");
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Task 1.3: file=null triggers createEmptyDocxSegment ──

    @Test
    void createSegment_fileNull_createsEmptyDocxAndUploadsToMinio() throws Exception {
        CreateSegmentRequest request = new CreateSegmentRequest();
        request.setName("Test Segment");

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));
        when(segmentRepository.save(any(Segment.class))).thenAnswer(inv -> {
            Segment s = inv.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
            return s;
        });

        SegmentDTO result = segmentService.createSegment(request, null, 10L);

        assertNotNull(result);
        assertEquals("Test Segment", result.getName());
        assertEquals(1L, result.getTenantId());
        assertNotNull(result.getFilePath());
        assertTrue(result.getFilePath().startsWith("segments/1/"));
        assertTrue(result.getFilePath().endsWith("_Test_Segment.docx"));

        // Verify MinIO putObject was called (for the empty docx upload)
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    // ── Task 1.3: generateEmptyDocx produces valid ZIP with 3 entries ──

    @Test
    void generateEmptyDocx_producesValidZipWithThreeEntries() throws Exception {
        // Access the private generateEmptyDocx method via reflection
        Method generateMethod = SegmentService.class.getDeclaredMethod("generateEmptyDocx");
        generateMethod.setAccessible(true);

        byte[] docxBytes = (byte[]) generateMethod.invoke(segmentService);

        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 0);

        // Parse as ZIP and collect entries
        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                // Read entry content to verify it's non-empty
                byte[] content = zis.readAllBytes();
                assertTrue(content.length > 0, "ZIP entry " + entry.getName() + " should have content");
                zis.closeEntry();
            }
        }

        assertEquals(3, entryNames.size(), "Empty .docx should contain exactly 3 ZIP entries");
        assertTrue(entryNames.contains("[Content_Types].xml"));
        assertTrue(entryNames.contains("_rels/.rels"));
        assertTrue(entryNames.contains("word/document.xml"));
    }

    // ── Task 1.3: MinIO upload failure throws BusinessException ──

    @Test
    void createSegment_fileNull_minioUploadFails_throwsBusinessException() throws Exception {
        CreateSegmentRequest request = new CreateSegmentRequest();
        request.setName("Fail Segment");

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> segmentService.createSegment(request, null, 10L));

        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());

        // Verify repository save was never called since upload failed
        verify(segmentRepository, never()).save(any(Segment.class));
    }
}
