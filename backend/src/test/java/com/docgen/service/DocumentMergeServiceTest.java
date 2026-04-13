package com.docgen.service;

import com.docgen.dto.MergeDocumentsRequest;
import com.docgen.entity.GeneratedDocument;
import com.docgen.exception.BusinessException;
import com.docgen.repository.GeneratedDocumentRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentMergeServiceTest {

    @Mock
    private GeneratedDocumentRepository documentRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private MinioClient minioClient;

    private DocumentMergeService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DocumentMergeService(documentRepository, restTemplate, minioClient);
        setField("docxtemplaterServiceUrl", "http://localhost:3000");
        setField("bucketName", "docgen");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = DocumentMergeService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    // ── Validation tests ──

    @Test
    void validateRequest_nullRequest_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateRequest(null));
        assertEquals("MERGE_INVALID_REQUEST", ex.getErrorCode());
    }

    @Test
    void validateRequest_emptyDocumentIds_throws() {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateRequest(req));
        assertEquals("MERGE_INVALID_REQUEST", ex.getErrorCode());
    }

    @Test
    void validateRequest_singleDocument_throws() {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateRequest(req));
        assertTrue(ex.getMessage().contains("至少需要 2 个文档"));
    }

    @Test
    void validateRequest_invalidFormat_throws() {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L));
        req.setOutputFormat("HTML");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateRequest(req));
        assertTrue(ex.getMessage().contains("不支持的输出格式"));
    }

    @Test
    void validateRequest_validDocxFormat_passes() {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L));
        req.setOutputFormat("DOCX");
        assertDoesNotThrow(() -> service.validateRequest(req));
    }

    @Test
    void validateRequest_validPdfFormat_passes() {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L));
        req.setOutputFormat("PDF");
        assertDoesNotThrow(() -> service.validateRequest(req));
    }

    // ── Invalid document IDs ──

    @Test
    void mergeDocuments_invalidDocumentIds_throws() {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L, 3L));

        when(documentRepository.findById(1L)).thenReturn(Optional.of(createDoc(1L)));
        when(documentRepository.findById(2L)).thenReturn(Optional.empty());
        when(documentRepository.findById(3L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.mergeDocuments(req));
        assertEquals("MERGE_INVALID_DOCUMENT_IDS", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("2"));
        assertTrue(ex.getMessage().contains("3"));
    }

    // ── Successful merge ──

    @Test
    @SuppressWarnings("unchecked")
    void mergeDocuments_success_docx() throws Exception {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L));
        req.setInsertPageBreaks(true);
        req.setGenerateToc(false);
        req.setOutputFormat("DOCX");

        GeneratedDocument doc1 = createDoc(1L);
        GeneratedDocument doc2 = createDoc(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));

        byte[] content1 = "doc1-content".getBytes();
        byte[] content2 = "doc2-content".getBytes();
        mockMinioGet(doc1.getFilePath(), content1);
        mockMinioGet(doc2.getFilePath(), content2);

        byte[] mergedBytes = "merged-result".getBytes();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mergedBytes, HttpStatus.OK));

        when(documentRepository.save(any(GeneratedDocument.class)))
                .thenAnswer(inv -> {
                    GeneratedDocument d = inv.getArgument(0);
                    d.setId(100L);
                    return d;
                });

        GeneratedDocument result = service.mergeDocuments(req);

        assertNotNull(result);
        assertEquals("DOCX", result.getFormat());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("PERSISTENT", result.getStorageStrategy());
        assertEquals((long) mergedBytes.length, result.getFileSize());
        assertTrue(result.getFilePath().startsWith("merged/"));
        assertTrue(result.getFilePath().endsWith(".docx"));

        // Verify Node.js service was called with correct params
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("http://localhost:3000/merge"),
                eq(HttpMethod.POST), captor.capture(), eq(byte[].class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals(2, ((List<?>) body.get("documents")).size());
        assertEquals(true, body.get("insertPageBreaks"));
        assertEquals(false, body.get("generateToc"));
        assertEquals("DOCX", body.get("outputFormat"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeDocuments_success_pdf() throws Exception {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L));
        req.setOutputFormat("PDF");

        GeneratedDocument doc1 = createDoc(1L);
        GeneratedDocument doc2 = createDoc(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));

        mockMinioGet(doc1.getFilePath(), "c1".getBytes());
        mockMinioGet(doc2.getFilePath(), "c2".getBytes());

        byte[] mergedBytes = "merged-pdf".getBytes();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mergedBytes, HttpStatus.OK));

        when(documentRepository.save(any(GeneratedDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GeneratedDocument result = service.mergeDocuments(req);

        assertEquals("PDF", result.getFormat());
        assertTrue(result.getFilePath().endsWith(".pdf"));
    }

    // ── Node.js service failure ──

    @Test
    void mergeDocuments_nodeServiceFailure_throws() throws Exception {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L));

        GeneratedDocument doc1 = createDoc(1L);
        GeneratedDocument doc2 = createDoc(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));

        mockMinioGet(doc1.getFilePath(), "c1".getBytes());
        mockMinioGet(doc2.getFilePath(), "c2".getBytes());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RestClientException("Connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.mergeDocuments(req));
        assertEquals("MERGE_FAILED", ex.getErrorCode());
    }

    @Test
    void mergeDocuments_nodeServiceReturnsEmpty_throws() throws Exception {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(1L, 2L));

        GeneratedDocument doc1 = createDoc(1L);
        GeneratedDocument doc2 = createDoc(2L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));

        mockMinioGet(doc1.getFilePath(), "c1".getBytes());
        mockMinioGet(doc2.getFilePath(), "c2".getBytes());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(new byte[0], HttpStatus.OK));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.mergeDocuments(req));
        assertEquals("MERGE_FAILED", ex.getErrorCode());
    }

    // ── Default values ──

    @Test
    void mergeDocumentsRequest_defaults() {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        assertTrue(req.isInsertPageBreaks());
        assertFalse(req.isGenerateToc());
        assertEquals("DOCX", req.getOutputFormat());
    }

    // ── Merge order preserved ──

    @Test
    @SuppressWarnings("unchecked")
    void mergeDocuments_preservesOrder() throws Exception {
        MergeDocumentsRequest req = new MergeDocumentsRequest();
        req.setDocumentIds(List.of(3L, 1L, 2L));

        GeneratedDocument doc1 = createDoc(1L);
        GeneratedDocument doc2 = createDoc(2L);
        GeneratedDocument doc3 = createDoc(3L);
        when(documentRepository.findById(3L)).thenReturn(Optional.of(doc3));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));

        mockMinioGet(doc3.getFilePath(), "content-3".getBytes());
        mockMinioGet(doc1.getFilePath(), "content-1".getBytes());
        mockMinioGet(doc2.getFilePath(), "content-2".getBytes());

        byte[] mergedBytes = "merged".getBytes();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mergedBytes, HttpStatus.OK));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.mergeDocuments(req);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(byte[].class));

        Map<String, Object> body = captor.getValue().getBody();
        List<String> docs = (List<String>) body.get("documents");
        // Verify order: doc3, doc1, doc2
        assertEquals(Base64.getEncoder().encodeToString("content-3".getBytes()), docs.get(0));
        assertEquals(Base64.getEncoder().encodeToString("content-1".getBytes()), docs.get(1));
        assertEquals(Base64.getEncoder().encodeToString("content-2".getBytes()), docs.get(2));
    }

    // ── Helpers ──

    private GeneratedDocument createDoc(Long id) {
        GeneratedDocument doc = new GeneratedDocument();
        doc.setId(id);
        doc.setTemplateId(10L);
        doc.setTenantId(1L);
        doc.setFilePath("documents/" + id + ".docx");
        doc.setFormat("DOCX");
        doc.setStatus("COMPLETED");
        doc.setStorageStrategy("PERSISTENT");
        return doc;
    }

    private void mockMinioGet(String path, byte[] content) throws Exception {
        var mockResponse = mock(io.minio.GetObjectResponse.class);
        when(mockResponse.readAllBytes()).thenReturn(content);
        when(minioClient.getObject(argThat((GetObjectArgs args) ->
                args != null && path.equals(args.object()))))
                .thenReturn(mockResponse);
    }
}
