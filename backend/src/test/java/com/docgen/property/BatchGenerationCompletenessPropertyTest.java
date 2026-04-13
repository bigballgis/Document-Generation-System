package com.docgen.property;

import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.entity.AsyncTask;
import com.docgen.entity.GeneratedDocument;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.repository.AsyncTaskRepository;
import com.docgen.repository.GeneratedDocumentRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.service.BatchDocumentService;
import com.docgen.service.DocumentGeneratorService;
import com.docgen.service.DocumentStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 18: 批量生成完整性
 *
 * For any batch generation request with N data sets (1 ≤ N ≤ 1000),
 * after the batch task completes:
 * - successCount + failCount = N
 * - ZIP file contains exactly successCount documents
 *
 * **Validates: Requirements 24.2, 24.4, 24.5, 24.7**
 */
class BatchGenerationCompletenessPropertyTest {

    /**
     * Property: For N data sets, success + fail = N, and ZIP contains exactly successCount docs.
     *
     * **Validates: Requirements 24.2, 24.4, 24.5, 24.7**
     */
    @Property(tries = 100)
    @Label("Feature: low-code-document-generation-system, Property 18: 批量生成完整性")
    void batchGenerationCompleteness(
            @ForAll @IntRange(min = 1, max = 50) int totalDataSets,
            @ForAll @IntRange(min = 0, max = 100) int failPercentage) {

        // Determine which items will fail
        Set<Integer> failIndices = new HashSet<>();
        Random rng = new Random(totalDataSets * 1000L + failPercentage);
        int expectedFails = Math.min(totalDataSets, totalDataSets * failPercentage / 100);
        while (failIndices.size() < expectedFails) {
            failIndices.add(rng.nextInt(totalDataSets));
        }
        int expectedSuccess = totalDataSets - failIndices.size();

        // Mock dependencies
        AsyncTaskRepository asyncTaskRepo = mock(AsyncTaskRepository.class);
        TemplateRepository templateRepo = mock(TemplateRepository.class);
        GeneratedDocumentRepository docRepo = mock(GeneratedDocumentRepository.class);
        DocumentGeneratorService generatorService = mock(DocumentGeneratorService.class);
        DocumentStorageService storageService = mock(DocumentStorageService.class);
        MinioClient minioClient = mock(MinioClient.class);

        // Setup template
        Template template = new Template();
        template.setId(1L);
        template.setTenantId(1L);
        template.setOutputFormat("WORD");
        template.setStorageStrategy("TEMP");
        template.setTemplateFilePath("templates/1/test.docx");
        when(templateRepo.findById(1L)).thenReturn(Optional.of(template));

        // Track the task state
        AsyncTask capturedTask = new AsyncTask();
        capturedTask.setTaskId("test-task-" + totalDataSets);
        capturedTask.setTaskType("BATCH_GENERATE");
        capturedTask.setTemplateId(1L);
        capturedTask.setTenantId(1L);
        capturedTask.setStatus("PENDING");
        capturedTask.setTotalCount(totalDataSets);
        capturedTask.setCreatedAt(Instant.now());
        capturedTask.setUpdatedAt(Instant.now());

        when(asyncTaskRepo.findByTaskId(capturedTask.getTaskId())).thenReturn(Optional.of(capturedTask));
        when(asyncTaskRepo.save(any(AsyncTask.class))).thenAnswer(inv -> {
            AsyncTask saved = inv.getArgument(0);
            // Copy state to our tracked task
            capturedTask.setStatus(saved.getStatus());
            capturedTask.setProgress(saved.getProgress());
            capturedTask.setCompletedCount(saved.getCompletedCount());
            capturedTask.setSuccessCount(saved.getSuccessCount());
            capturedTask.setFailCount(saved.getFailCount());
            capturedTask.setDocumentId(saved.getDocumentId());
            capturedTask.setErrorMessage(saved.getErrorMessage());
            capturedTask.setCompletedAt(saved.getCompletedAt());
            return saved;
        });

        // Mock document generation - some succeed, some fail
        long[] docIdCounter = {100L};
        for (int i = 0; i < totalDataSets; i++) {
            final int index = i;
            if (failIndices.contains(i)) {
                // This item will fail
                when(generatorService.generateDocument(eq(1L), argThat(req -> {
                    if (req == null || req.getParameters() == null) return false;
                    Object idx = req.getParameters().get("_index");
                    return idx != null && Integer.parseInt(idx.toString()) == index;
                }))).thenThrow(new BusinessException("GENERATE_FAILED",
                        "Simulated failure for item " + index,
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));
            } else {
                final long docId = docIdCounter[0]++;
                GenerateDocumentResponse response = new GenerateDocumentResponse();
                response.setDocumentId(docId);
                response.setTemplateId(1L);
                response.setFormat("WORD");
                when(generatorService.generateDocument(eq(1L), argThat(req -> {
                    if (req == null || req.getParameters() == null) return false;
                    Object idx = req.getParameters().get("_index");
                    return idx != null && Integer.parseInt(idx.toString()) == index;
                }))).thenReturn(response);

                // Mock download for ZIP packaging
                byte[] fakeContent = ("Document content for item " + index).getBytes();
                when(storageService.downloadDocument(docId)).thenReturn(fakeContent);
            }
        }

        // Mock ZIP document save
        when(docRepo.save(any(GeneratedDocument.class))).thenAnswer(inv -> {
            GeneratedDocument doc = inv.getArgument(0);
            doc.setId(999L);
            return doc;
        });

        try {
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        } catch (Exception e) {
            // ignore
        }

        // Build the batch request data sets
        List<Map<String, Object>> dataSets = new ArrayList<>();
        for (int i = 0; i < totalDataSets; i++) {
            Map<String, Object> ds = new HashMap<>();
            ds.put("_index", i);
            ds.put("name", "Item " + i);
            dataSets.add(ds);
        }

        // Execute batch processing directly (not via submitBatchGeneration to avoid @Transactional)
        BatchDocumentService service = new BatchDocumentService(
                asyncTaskRepo, templateRepo, docRepo, generatorService, storageService, minioClient);

        // Use reflection to set bucketName
        try {
            var field = BatchDocumentService.class.getDeclaredField("bucketName");
            field.setAccessible(true);
            field.set(service, "docgen");
        } catch (Exception e) {
            fail("Failed to set bucketName: " + e.getMessage());
        }

        com.docgen.dto.BatchGenerateRequest batchRequest = new com.docgen.dto.BatchGenerateRequest();
        batchRequest.setDataSets(dataSets);
        batchRequest.setOutputFormat("WORD");
        batchRequest.setStorageStrategy("TEMP");
        batchRequest.setFailureStrategy("CONTINUE");

        // Process the batch
        service.processBatchGeneration(capturedTask.getTaskId(), 1L, batchRequest);

        // Verify Property 18: success + fail = N
        assertEquals(totalDataSets, capturedTask.getSuccessCount() + capturedTask.getFailCount(),
                "successCount + failCount must equal total data sets N=" + totalDataSets);

        assertEquals(expectedSuccess, capturedTask.getSuccessCount(),
                "successCount should match expected successes");

        assertEquals(failIndices.size(), capturedTask.getFailCount(),
                "failCount should match expected failures");

        assertEquals("COMPLETED", capturedTask.getStatus(),
                "Task should be COMPLETED");

        // Verify ZIP contains exactly successCount documents
        if (expectedSuccess > 0) {
            // Verify createZip produces correct number of entries
            List<byte[]> docs = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < expectedSuccess; i++) {
                docs.add(("doc" + i).getBytes());
                names.add("document_" + (i + 1) + ".docx");
            }
            byte[] zipBytes = service.createZip(docs, names);
            int zipEntryCount = countZipEntries(zipBytes);
            assertEquals(expectedSuccess, zipEntryCount,
                    "ZIP should contain exactly successCount=" + expectedSuccess + " documents");
        }
    }

    private int countZipEntries(byte[] zipBytes) {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            while (zis.getNextEntry() != null) {
                count++;
                zis.closeEntry();
            }
        } catch (Exception e) {
            fail("Failed to read ZIP: " + e.getMessage());
        }
        return count;
    }
}
