package com.docgen.property;

import com.docgen.dto.MergeDocumentsRequest;
import com.docgen.entity.GeneratedDocument;
import com.docgen.repository.GeneratedDocumentRepository;
import com.docgen.service.DocumentMergeService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.mockito.ArgumentCaptor;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 19: document merge order correctness
 *
 * For any document ID list [A, B, C], the base64 segment buffers sent to the
 * Node.js {@code /merge-segments} endpoint must be in the same order as the input document ID list.
 *
 * **Validates: Requirements 40.1, 40.3, 40.4**
 */
class DocumentMergeOrderPropertyTest {

    /**
     * Property: The order of segment buffers sent to {@code /merge-segments} matches
     * the order of document IDs in the request.
     *
     * **Validates: Requirements 40.1, 40.3, 40.4**
     */
    @Property(tries = 100)
    @Label("Feature: low-code-document-generation-system, Property 19: document merge order correctness")
    @SuppressWarnings("unchecked")
    void mergeOrderMatchesInputOrder(
            @ForAll @Size(min = 2, max = 10) List<@IntRange(min = 1, max = 1000) Integer> docIdValues) {

        // Deduplicate while preserving order
        List<Long> documentIds = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Integer v : docIdValues) {
            long id = v.longValue();
            if (seen.add(id)) {
                documentIds.add(id);
            }
        }
        // Need at least 2 unique IDs for a valid merge request
        if (documentIds.size() < 2) {
            return; // skip this case
        }

        // Mock dependencies
        GeneratedDocumentRepository documentRepository = mock(GeneratedDocumentRepository.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        MinioClient minioClient = mock(MinioClient.class);

        DocumentMergeService service = new DocumentMergeService(documentRepository, restTemplate, minioClient);
        setField(service, "docxtemplaterServiceUrl", "http://localhost:3000");
        setField(service, "bucketName", "docgen");

        // Create unique content per document so we can verify order
        Map<Long, String> contentByDocId = new LinkedHashMap<>();
        for (Long id : documentIds) {
            contentByDocId.put(id, "content-for-doc-" + id);
        }

        // Mock repository and MinIO for each document
        for (Long id : documentIds) {
            GeneratedDocument doc = new GeneratedDocument();
            doc.setId(id);
            doc.setTemplateId(10L);
            doc.setTenantId(1L);
            doc.setFilePath("documents/" + id + ".docx");
            doc.setFormat("DOCX");
            doc.setStatus("COMPLETED");
            doc.setStorageStrategy("PERSISTENT");
            when(documentRepository.findById(id)).thenReturn(Optional.of(doc));

            byte[] content = contentByDocId.get(id).getBytes();
            try {
                var mockResponse = mock(io.minio.GetObjectResponse.class);
                when(mockResponse.readAllBytes()).thenReturn(content);
                when(minioClient.getObject(argThat((GetObjectArgs args) ->
                        args != null && ("documents/" + id + ".docx").equals(args.object()))))
                        .thenReturn(mockResponse);
            } catch (Exception e) {
                fail("Failed to mock MinIO: " + e.getMessage());
            }
        }

        // Mock the Node.js merge endpoint to return non-empty bytes
        byte[] mergedBytes = "merged-result".getBytes();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(mergedBytes, HttpStatus.OK));

        // Mock save
        when(documentRepository.save(any(GeneratedDocument.class)))
                .thenAnswer(inv -> {
                    GeneratedDocument d = inv.getArgument(0);
                    d.setId(9999L);
                    return d;
                });

        // Mock MinIO upload
        try {
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        } catch (Exception e) {
            // ignore
        }

        // Build request
        MergeDocumentsRequest request = new MergeDocumentsRequest();
        request.setDocumentIds(documentIds);
        request.setInsertPageBreaks(true);
        request.setOutputFormat("DOCX");

        // Execute merge
        service.mergeDocuments(request);

        // Capture the request sent to the Node.js merge endpoint
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:3000/merge-segments"),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(byte[].class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body, "Request body must not be null");

        List<Map<String, Object>> segments = (List<Map<String, Object>>) body.get("segments");
        assertNotNull(segments, "segments list must not be null");
        assertEquals(documentIds.size(), segments.size(),
                "Number of segments sent must match number of input IDs");

        // Verify order: the i-th segment buffer corresponds to the i-th document ID
        for (int i = 0; i < documentIds.size(); i++) {
            Long expectedId = documentIds.get(i);
            String expectedBase64 = Base64.getEncoder()
                    .encodeToString(contentByDocId.get(expectedId).getBytes());
            assertEquals(expectedBase64, segments.get(i).get("buffer"),
                    "Segment at position " + i + " should be content of doc ID " + expectedId);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            fail("Failed to set field " + name + ": " + e.getMessage());
        }
    }
}
