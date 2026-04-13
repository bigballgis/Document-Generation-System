package com.docgen.service;

import com.docgen.dto.ApiCallMetricDTO;
import com.docgen.dto.ComponentRankingDTO;
import com.docgen.dto.DataSourceHealthDTO;
import com.docgen.dto.SegmentStatsDTO;
import com.docgen.dto.SystemOverviewDTO;
import com.docgen.dto.SystemResourceDTO;
import com.docgen.entity.DataSource;
import com.docgen.entity.Segment;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.GeneratedDocumentRepository;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.TemplateRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Service providing dashboard metrics: system overview, API call trends,
 * data source health, and system resource usage.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final TemplateRepository templateRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final DataSourceRepository dataSourceRepository;
    private final MeterRegistry meterRegistry;
    private final RedisConnectionFactory redisConnectionFactory;
    private final SegmentRepository segmentRepository;
    private final DependencyGraphService dependencyGraphService;

    public DashboardService(TemplateRepository templateRepository,
                            GeneratedDocumentRepository generatedDocumentRepository,
                            DataSourceRepository dataSourceRepository,
                            MeterRegistry meterRegistry,
                            RedisConnectionFactory redisConnectionFactory,
                            SegmentRepository segmentRepository,
                            DependencyGraphService dependencyGraphService) {
        this.templateRepository = templateRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.meterRegistry = meterRegistry;
        this.redisConnectionFactory = redisConnectionFactory;
        this.segmentRepository = segmentRepository;
        this.dependencyGraphService = dependencyGraphService;
    }

    // ── System Overview ──

    /**
     * Returns high-level system counts: total templates, active templates,
     * total API calls (from Micrometer counter), and total generated documents.
     */
    public SystemOverviewDTO getSystemOverview() {
        long totalTemplates = templateRepository.count();
        long activeTemplates = templateRepository.countByStatus("ACTIVE");
        long totalApiCalls = getTotalApiCallCount();
        long totalDocuments = generatedDocumentRepository.count();

        SystemOverviewDTO dto = new SystemOverviewDTO(totalTemplates, activeTemplates, totalApiCalls, totalDocuments);
        dto.setSegmentCount(segmentRepository.count());
        dto.setComponentCount(segmentRepository.countByComponent(true));
        dto.setCompositeTemplateCount(templateRepository.countByTemplateType("COMPOSITE"));
        return dto;
    }

    // ── API Call Metrics ──

    /**
     * Returns API call volume data points for the last {@code minutes} minutes,
     * bucketed into 1-minute intervals. Defaults to 60 minutes (1 hour).
     */
    public List<ApiCallMetricDTO> getApiCallMetrics(int minutes) {
        if (minutes <= 0) {
            minutes = 60;
        }
        // Micrometer timers are cumulative; we return the current snapshot
        // bucketed by minute for the requested window.
        List<ApiCallMetricDTO> metrics = new ArrayList<>();
        Instant now = Instant.now();

        // Collect aggregate counts and mean from the api.request.duration timer
        double totalCount = 0;
        double totalTime = 0;

        for (Timer timer : meterRegistry.find("api.request.duration").timers()) {
            totalCount += timer.count();
            totalTime += timer.totalTime(TimeUnit.MILLISECONDS);
        }

        double avgResponseTime = totalCount > 0 ? totalTime / totalCount : 0;

        // Produce a single current-snapshot data point per minute bucket
        // Since Micrometer doesn't store per-minute history, we provide the
        // current aggregate as the latest data point and zero-fill the rest.
        for (int i = minutes - 1; i >= 1; i--) {
            Instant bucketTime = now.minus(Duration.ofMinutes(i));
            metrics.add(new ApiCallMetricDTO(bucketTime, 0, 0));
        }
        // Latest bucket carries the cumulative snapshot
        metrics.add(new ApiCallMetricDTO(now, (long) totalCount, avgResponseTime));

        return metrics;
    }

    // ── Data Source Health ──

    /**
     * Returns health status for every registered data source.
     * Connectivity is tested by checking if the data source config is parseable
     * and the type is known. Average response time comes from Micrometer if available.
     */
    public List<DataSourceHealthDTO> getDataSourceHealth() {
        List<DataSource> dataSources = dataSourceRepository.findAll();
        List<DataSourceHealthDTO> healthList = new ArrayList<>();

        for (DataSource ds : dataSources) {
            DataSourceHealthDTO dto = new DataSourceHealthDTO();
            dto.setId(ds.getId());
            dto.setName(ds.getName());
            dto.setType(ds.getType());

            // Check basic reachability: config must be non-empty and type known
            boolean reachable = ds.getConfigJson() != null && !ds.getConfigJson().isBlank()
                    && isKnownType(ds.getType());
            dto.setReachable(reachable);

            // Try to get average response time from Micrometer timer tagged by data source name
            double avgMs = getDataSourceAvgResponseTime(ds.getName());
            dto.setAvgResponseTimeMs(avgMs);

            if (!reachable) {
                dto.setLastError("Data source configuration is missing or type is unknown");
            }

            healthList.add(dto);
        }
        return healthList;
    }

    // ── System Resources ──

    /**
     * Collects JVM heap memory, HikariCP connection pool, and Redis memory usage.
     */
    public SystemResourceDTO getSystemResources() {
        SystemResourceDTO dto = new SystemResourceDTO();
        dto.setJvmMemory(collectJvmMemory());
        dto.setDbPool(collectDbPool());
        dto.setRedisMemory(collectRedisMemory());
        return dto;
    }

    // ── Private helpers ──

    /**
     * Returns segment statistics: total segments, component vs regular, composite vs single templates.
     */
    public SegmentStatsDTO getSegmentStats() {
        SegmentStatsDTO stats = new SegmentStatsDTO();
        stats.setTotalSegments(segmentRepository.count());
        stats.setComponentSegments(segmentRepository.countByComponent(true));
        stats.setRegularSegments(segmentRepository.countByComponent(false));
        stats.setCompositeTemplates(templateRepository.countByTemplateType("COMPOSITE"));
        stats.setSingleTemplates(templateRepository.countByTemplateType("SINGLE"));
        return stats;
    }

    /**
     * Returns the top 10 component templates ranked by reference count (descending).
     */
    public List<ComponentRankingDTO> getComponentRanking() {
        List<Segment> components = segmentRepository.findAll().stream()
                .filter(Segment::isComponent)
                .toList();

        return components.stream()
                .map(seg -> new ComponentRankingDTO(
                        seg.getId(),
                        seg.getName(),
                        dependencyGraphService.getReferenceCount(seg.getId())))
                .sorted((a, b) -> Integer.compare(b.getReferenceCount(), a.getReferenceCount()))
                .limit(10)
                .toList();
    }

    // ── Private helpers ──

    private long getTotalApiCallCount() {
        double total = 0;
        for (Counter counter : meterRegistry.find("api.request.count").counters()) {
            total += counter.count();
        }
        return (long) total;
    }

    private boolean isKnownType(String type) {
        if (type == null) return false;
        return switch (type.toUpperCase()) {
            case "HTTP_API", "DATABASE", "INTERNAL_SYSTEM" -> true;
            default -> false;
        };
    }

    private double getDataSourceAvgResponseTime(String dataSourceName) {
        // Convention: timers tagged with datasource=<name>
        Search search = meterRegistry.find("datasource.request.duration")
                .tag("datasource", dataSourceName);
        Timer timer = search.timer();
        if (timer != null && timer.count() > 0) {
            return timer.totalTime(TimeUnit.MILLISECONDS) / timer.count();
        }
        return 0;
    }

    private SystemResourceDTO.JvmMemory collectJvmMemory() {
        long usedBytes = 0;
        long maxBytes = 0;

        // Micrometer auto-registers jvm.memory.used and jvm.memory.max gauges
        for (Gauge gauge : meterRegistry.find("jvm.memory.used").gauges()) {
            usedBytes += (long) gauge.value();
        }
        for (Gauge gauge : meterRegistry.find("jvm.memory.max").gauges()) {
            double val = gauge.value();
            if (val > 0) {
                maxBytes += (long) val;
            }
        }

        double usagePercent = maxBytes > 0 ? (double) usedBytes / maxBytes * 100 : 0;
        return new SystemResourceDTO.JvmMemory(usedBytes, maxBytes, Math.round(usagePercent * 100.0) / 100.0);
    }

    private SystemResourceDTO.DbPool collectDbPool() {
        SystemResourceDTO.DbPool pool = new SystemResourceDTO.DbPool();

        // HikariCP exposes metrics via Micrometer with prefix hikaricp
        Gauge activeGauge = meterRegistry.find("hikaricp.connections.active").gauge();
        Gauge idleGauge = meterRegistry.find("hikaricp.connections.idle").gauge();
        Gauge totalGauge = meterRegistry.find("hikaricp.connections").gauge();
        Gauge maxGauge = meterRegistry.find("hikaricp.connections.max").gauge();

        int active = activeGauge != null ? (int) activeGauge.value() : 0;
        int idle = idleGauge != null ? (int) idleGauge.value() : 0;
        int total = totalGauge != null ? (int) totalGauge.value() : 0;
        int max = maxGauge != null ? (int) maxGauge.value() : 0;

        pool.setActiveConnections(active);
        pool.setIdleConnections(idle);
        pool.setTotalConnections(total);
        pool.setMaxConnections(max);
        pool.setUsagePercent(max > 0 ? Math.round((double) active / max * 10000.0) / 100.0 : 0);

        return pool;
    }

    private SystemResourceDTO.RedisMemory collectRedisMemory() {
        long usedMemory = 0;
        long maxMemory = 0;

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Properties info = connection.serverCommands().info("memory");
            if (info != null) {
                String usedStr = info.getProperty("used_memory");
                String maxStr = info.getProperty("maxmemory");
                if (usedStr != null) {
                    usedMemory = Long.parseLong(usedStr);
                }
                if (maxStr != null) {
                    maxMemory = Long.parseLong(maxStr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve Redis memory info: {}", e.getMessage());
        }

        double usagePercent = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
        return new SystemResourceDTO.RedisMemory(usedMemory, maxMemory,
                Math.round(usagePercent * 100.0) / 100.0);
    }
}
