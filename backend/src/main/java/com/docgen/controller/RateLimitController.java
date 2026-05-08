package com.docgen.controller;

import com.docgen.dto.RateLimitStatusDTO;
import com.docgen.dto.UserPrincipal;
import com.docgen.dto.UsageStatsDTO;
import com.docgen.entity.ApiKey;
import com.docgen.repository.ApiKeyRepository;
import com.docgen.service.RateLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RateLimitController {

    private final RateLimitService rateLimitService;
    private final ApiKeyRepository apiKeyRepository;

    public RateLimitController(RateLimitService rateLimitService,
                               ApiKeyRepository apiKeyRepository) {
        this.rateLimitService = rateLimitService;
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * GET /api/rate-limits — Query rate limit status for all API keys of the current tenant.
     */
    @GetMapping("/rate-limits")
    public ResponseEntity<List<RateLimitStatusDTO>> getRateLimits(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        List<ApiKey> keys = apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        List<RateLimitStatusDTO> statuses = keys.stream()
                .map(key -> rateLimitService.getRateLimitStatus(
                        key.getId(),
                        key.getName(),
                        key.getRateLimitPerSecond(),
                        key.getRateLimitPerMinute(),
                        key.getRateLimitPerHour()))
                .toList();

        return ResponseEntity.ok(statuses);
    }

    /**
     * GET /api/usage-stats — Query usage statistics for the current tenant.
     */
    @GetMapping("/usage-stats")
    public ResponseEntity<UsageStatsDTO> getUsageStats(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        UsageStatsDTO stats = rateLimitService.getUsageStats(tenantId);
        return ResponseEntity.ok(stats);
    }
}
