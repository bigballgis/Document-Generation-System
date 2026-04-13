package com.docgen.controller;

import com.docgen.dto.AsyncTaskDTO;
import com.docgen.dto.BatchGenerateRequest;
import com.docgen.dto.GenerateDocumentRequest;
import com.docgen.dto.GenerateDocumentResponse;
import com.docgen.service.AsyncDocumentService;
import com.docgen.service.BatchDocumentService;
import com.docgen.service.DynamicApiService;
import com.docgen.service.DocumentGeneratorService;
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

    private final DocumentGeneratorService documentGeneratorService;
    private final DynamicApiService dynamicApiService;
    private final AsyncDocumentService asyncDocumentService;
    private final BatchDocumentService batchDocumentService;

    public GenerateController(DocumentGeneratorService documentGeneratorService,
                              DynamicApiService dynamicApiService,
                              AsyncDocumentService asyncDocumentService,
                              BatchDocumentService batchDocumentService) {
        this.documentGeneratorService = documentGeneratorService;
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
            description = "Generate a document from an activated template. "
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
     * Asynchronously generate a document. Returns a task ID immediately.
     */
    @Operation(summary = "Generate document asynchronously",
            description = "Submit an async document generation task. Returns a task ID for status polling.")
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
}
