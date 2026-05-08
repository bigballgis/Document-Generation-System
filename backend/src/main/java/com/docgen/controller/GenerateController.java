package com.docgen.controller;

import com.docgen.dto.AsyncTaskDTO;
import com.docgen.dto.BatchGenerateRequest;
import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.service.AsyncDocumentService;
import com.docgen.service.BatchDocumentService;
import com.docgen.service.DynamicApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for document generation endpoints.
 * Supports synchronous, asynchronous, and batch generation.
 */
@RestController
@RequestMapping("/api/generate")
@Tag(name = "Document Generation", description = "APIs for generating documents from templates")
public class GenerateController {

    private final DynamicApiService dynamicApiService;
    private final AsyncDocumentService asyncDocumentService;
    private final BatchDocumentService batchDocumentService;

    public GenerateController(DynamicApiService dynamicApiService,
                              AsyncDocumentService asyncDocumentService,
                              BatchDocumentService batchDocumentService) {
        this.dynamicApiService = dynamicApiService;
        this.asyncDocumentService = asyncDocumentService;
        this.batchDocumentService = batchDocumentService;
    }

    /**
     * Synchronously generate a document for the given template.
     * Supports version selection via ?version={versionNumber}.
     * Only ACTIVE templates are callable. Defaults to latest active version.
     */
    @Operation(summary = "Generate document synchronously",
            description = "Generates one document per request (WORD or PDF via outputFormat). "
                    + "outputFormat BOTH is not supported — use /word and /pdf endpoints separately. "
                    + "Supports ?version={versionNumber} to use a specific template version.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document generated successfully",
                    content = @Content(schema = @Schema(implementation = GenerateDocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid version or parameters"),
            @ApiResponse(responseCode = "404", description = "Template not found or not active")
    })
    @PostMapping("/{templateId}")
    public ResponseEntity<GenerateDocumentResponse> generateDocument(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @Parameter(description = "Version number (optional, defaults to latest)")
            @RequestParam(required = false) Integer version,
            @RequestBody(required = false) GenerateDocumentRequest request) {
        if (request == null) {
            request = new GenerateDocumentRequest();
        }
        GenerateDocumentResponse response = dynamicApiService.generateViaApi(templateId, version, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Async Word-only generation (forces WORD output).
     */
    @Operation(summary = "Generate Word document asynchronously")
    @PostMapping("/{templateId}/async/word")
    public ResponseEntity<AsyncTaskDTO> generateDocumentAsyncWord(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @RequestBody(required = false) GenerateDocumentRequest request) {
        if (request == null) {
            request = new GenerateDocumentRequest();
        }
        request.setOutputFormat("WORD");
        AsyncTaskDTO task = asyncDocumentService.submitAsyncGeneration(templateId, request);
        return ResponseEntity.accepted().body(task);
    }

    /**
     * Async PDF-only generation (forces PDF output).
     */
    @Operation(summary = "Generate PDF document asynchronously")
    @PostMapping("/{templateId}/async/pdf")
    public ResponseEntity<AsyncTaskDTO> generateDocumentAsyncPdf(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @RequestBody(required = false) GenerateDocumentRequest request) {
        if (request == null) {
            request = new GenerateDocumentRequest();
        }
        request.setOutputFormat("PDF");
        AsyncTaskDTO task = asyncDocumentService.submitAsyncGeneration(templateId, request);
        return ResponseEntity.accepted().body(task);
    }

    /**
     * Asynchronously generate a document. Returns a task ID immediately.
     */
    @Operation(summary = "Generate document asynchronously",
            description = "Submit an async document generation task. outputFormat BOTH is not supported — "
                    + "use /async/word and /async/pdf separately when both formats are needed.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Task accepted",
                    content = @Content(schema = @Schema(implementation = AsyncTaskDTO.class))),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PostMapping("/{templateId}/async")
    public ResponseEntity<AsyncTaskDTO> generateDocumentAsync(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @RequestBody(required = false) GenerateDocumentRequest request) {
        if (request == null) {
            request = new GenerateDocumentRequest();
        }
        AsyncTaskDTO task = asyncDocumentService.submitAsyncGeneration(templateId, request);
        return ResponseEntity.accepted().body(task);
    }

    /**
     * Batch generate documents. Supports up to 1000 data sets per request.
     */
    @Operation(summary = "Batch generate documents",
            description = "Submit a batch document generation task. Max 1000 data sets per request.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Batch task accepted",
                    content = @Content(schema = @Schema(implementation = AsyncTaskDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or exceeds 1000 limit"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PostMapping("/{templateId}/batch")
    public ResponseEntity<AsyncTaskDTO> generateDocumentsBatch(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @RequestBody BatchGenerateRequest request) {
        AsyncTaskDTO task = batchDocumentService.submitBatchGeneration(templateId, request);
        return ResponseEntity.accepted().body(task);
    }

    /**
     * Generate only Word (.docx); ignores conflicting outputFormat in body (handled server-side).
     */
    @Operation(summary = "Generate Word document only",
            description = "Same body as synchronous generation but forces WORD output.")
    @PostMapping("/{templateId}/word")
    public ResponseEntity<GenerateDocumentResponse> generateWordOnly(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @Parameter(description = "Version number (optional, defaults to latest)")
            @RequestParam(required = false) Integer version,
            @RequestBody(required = false) GenerateDocumentRequest request) {
        if (request == null) {
            request = new GenerateDocumentRequest();
        }
        request.setOutputFormat("WORD");
        GenerateDocumentResponse response = dynamicApiService.generateViaApi(templateId, version, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate only PDF; ignores conflicting outputFormat in body (handled server-side).
     */
    @Operation(summary = "Generate PDF only",
            description = "Same body as synchronous generation but forces PDF output.")
    @PostMapping("/{templateId}/pdf")
    public ResponseEntity<GenerateDocumentResponse> generatePdfOnly(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @Parameter(description = "Version number (optional, defaults to latest)")
            @RequestParam(required = false) Integer version,
            @RequestBody(required = false) GenerateDocumentRequest request) {
        if (request == null) {
            request = new GenerateDocumentRequest();
        }
        request.setOutputFormat("PDF");
        GenerateDocumentResponse response = dynamicApiService.generateViaApi(templateId, version, request);
        return ResponseEntity.ok(response);
    }
}
