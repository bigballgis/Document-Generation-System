package com.docgen.controller;

import com.docgen.dto.ApiCallMetricDTO;
import com.docgen.dto.ComponentRankingDTO;
import com.docgen.dto.DataSourceHealthDTO;
import com.docgen.dto.SegmentStatsDTO;
import com.docgen.dto.SystemOverviewDTO;
import com.docgen.dto.SystemResourceDTO;
import com.docgen.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing dashboard endpoints for the operations dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Operations dashboard endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    @Operation(summary = "System overview",
            description = "Returns template counts, API call totals, and document totals")
    public ResponseEntity<SystemOverviewDTO> getOverview() {
        return ResponseEntity.ok(dashboardService.getSystemOverview());
    }

    @GetMapping("/api-metrics")
    @Operation(summary = "API call metrics",
            description = "Returns API call volume trend for the specified time window (default 60 minutes)")
    public ResponseEntity<List<ApiCallMetricDTO>> getApiMetrics(
            @RequestParam(defaultValue = "60") int minutes) {
        return ResponseEntity.ok(dashboardService.getApiCallMetrics(minutes));
    }

    @GetMapping("/data-source-health")
    @Operation(summary = "Data source health",
            description = "Returns connectivity and response time for all registered data sources")
    public ResponseEntity<List<DataSourceHealthDTO>> getDataSourceHealth() {
        return ResponseEntity.ok(dashboardService.getDataSourceHealth());
    }

    @GetMapping("/system-resources")
    @Operation(summary = "System resource usage",
            description = "Returns JVM memory, database connection pool, and Redis memory usage")
    public ResponseEntity<SystemResourceDTO> getSystemResources() {
        return ResponseEntity.ok(dashboardService.getSystemResources());
    }

    @GetMapping("/segment-stats")
    @Operation(summary = "Segment statistics",
            description = "Returns segment and composite template statistics")
    public ResponseEntity<SegmentStatsDTO> getSegmentStats() {
        return ResponseEntity.ok(dashboardService.getSegmentStats());
    }

    @GetMapping("/component-ranking")
    @Operation(summary = "Component reuse ranking",
            description = "Returns top 10 component templates ranked by reference count")
    public ResponseEntity<List<ComponentRankingDTO>> getComponentRanking() {
        return ResponseEntity.ok(dashboardService.getComponentRanking());
    }
}
