package com.docgen.service;

import com.docgen.dto.ApiCallMetricDTO;
import com.docgen.dto.DataSourceHealthDTO;
import com.docgen.dto.SystemOverviewDTO;
import com.docgen.dto.SystemResourceDTO;
import com.docgen.entity.DataSource;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.GeneratedDocumentRepository;
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

    public DashboardService(TemplateRepository templateRepository,
                            GeneratedDocumentRepository generatedDocumentRepository,
                            DataSourceRepository dataSourceRepository,
                            MeterRegistry meterRegistry,
                            RedisConnectionFactory redisConnectionFactory) {
        this.templateRepository = templateRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.meterRegistry = meterRegistry;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    // ── System Overview ──

    public SystemOverviewDTO getSystemOverview() {
        long totalTemplates = templateRepository.count();
        long activeTemplates = templateRepository.countByStatus("ACTIVE");
        long totalApiCalls = getTotalApiCallCount();
        long totalDocuments = generatedDocumentRepository.count();

        SystemOverviewDTO dto = new SystemOverviewDTO(totalTemplates, activeTemplates, totalApiCalls, totalDocuments);
        dto.setCompositeTemplateCount(templateRepository.countByTemplateType("COMPOSITE"));
        return dto;
    }

    // ── API Call Metrics ──

    public List<ApiCallMetricDTO> getApiCallMetrics(int minutes) {
        if (minutes <= 0) {
            minutes = 60;
        }
        List<ApiCallMetricDTO> metrics = new ArrayList<>();
        Instant now = Instant.now();

        double totalCount = 0;
        double totalTime = 0;

        for (Timer timer : meterRegistry.find("api.request.duration").timers()) {
            totalCount += timer.count();
            totalTime += timer.totalTime(TimeUnit.MILLISECONDS);
        }

        double avgResponseTime = totalCount > 0 ? totalTime / totalCount : 0;

        for (int i = minutes - 1; i >= 1; i--) {
            Instant bucketTime = now.minus(Duration.ofMinutes(i));
            metrics.add(new ApiCallMetricDTO(bucketTime, 0, 0));
        }
        metrics.add(new ApiCallMetricDTO(now, (long) totalCount, avgResponseTime));

        return metrics;
    }

    // ── Data Source Health ──

    public List<DataSourceHealthDTO> getDataSourceHealth() {
        List<DataSource> dataSources = dataSourceRepository.findAll();
        List<DataSourceHealthDTO> healthList = new ArrayList<>();

        for (DataSource ds : dataSources) {
            DataSourceHealthDTO dto = new DataSourceHealthDTO();
            dto.setId(ds.getId());
            dto.setName(ds.getName());
            dto.setType(ds.getType());

            boolean reachable = ds.getConfigJson() != null && !ds.getConfigJson().isBlank()
                    && isKnownType(ds.getType());
            dto.setReachable(reachable);

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

    public SystemResourceDTO getSystemResources() {
        SystemResourceDTO dto = new SystemResourceDTO();
        dto.setJvmMemory(collectJvmMemory());
        dto.setDbPool(collectDbPool());
        dto.setRedisMemory(collectRedisMemory());
        return dto;
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
