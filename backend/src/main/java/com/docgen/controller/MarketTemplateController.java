package com.docgen.controller;

import com.docgen.dto.MarketTemplateDTO;
import com.docgen.dto.ShareTemplateRequest;
import com.docgen.dto.TemplateDTO;
import com.docgen.service.CompositeMarketService;
import com.docgen.service.TemplateMarketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Search, copy, and share templates published to the market.
 */
@RestController
public class MarketTemplateController {

    private final TemplateMarketService templateMarketService;
    private final CompositeMarketService compositeMarketService;

    public MarketTemplateController(TemplateMarketService templateMarketService,
                                    CompositeMarketService compositeMarketService) {
        this.templateMarketService = templateMarketService;
        this.compositeMarketService = compositeMarketService;
    }

    /**
     * Search market templates by keyword and optional category.
     * Sanitizes sort fields: 'popular' maps to 'usageCount', unknown fields default to 'createdAt'.
     */
    @GetMapping("/api/market/templates")
    public ResponseEntity<Page<MarketTemplateDTO>> searchMarketTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        return ResponseEntity.ok(templateMarketService.searchMarketTemplates(keyword, categoryId, sanitized));
    }

    private Pageable sanitizePageable(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        if (order == null) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "created_at"));
        }
        String prop = order.getProperty();
        // Map frontend sort aliases / camelCase to actual DB column names (native query)
        String mapped = switch (prop) {
            case "popular", "usageCount" -> "usage_count";
            case "rating" -> "rating";
            case "name" -> "name";
            case "createdAt", "created_at" -> "created_at";
            default -> "created_at";
        };
        Sort sort = Sort.by(order.getDirection(), mapped);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * Copy a market template to the current user's workspace.
     */
    @PostMapping("/api/market/templates/{id}/copy")
    public ResponseEntity<TemplateDTO> copyFromMarket(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateMarketService.copyFromMarket(id));
    }

    /**
     * Share a template to the market.
     */
    @PostMapping("/api/templates/{id}/share")
    public ResponseEntity<MarketTemplateDTO> shareToMarket(
            @PathVariable Long id,
            @Valid @RequestBody ShareTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateMarketService.shareToMarket(id, request.getShareScope()));
    }

    /**
     * Copy a composite market template to the current user's workspace.
     * Creates a fully independent copy with all segments.
     */
    @PostMapping("/api/market/templates/{id}/copy-composite")
    public ResponseEntity<TemplateDTO> copyCompositeFromMarket(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(compositeMarketService.copyCompositeFromMarket(id));
    }

    /**
     * Share a composite template to the market.
     */
    @PostMapping("/api/composite-templates/{id}/share")
    public ResponseEntity<MarketTemplateDTO> shareCompositeToMarket(
            @PathVariable Long id,
            @Valid @RequestBody ShareTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(compositeMarketService.shareCompositeToMarket(id, request.getShareScope()));
    }
}
