package com.docgen.service;

import com.docgen.dto.ApiCallMetricDTO;
import com.docgen.dto.DataSourceHealthDTO;
import com.docgen.dto.SystemOverviewDTO;
import com.docgen.dto.SystemResourceDTO;
import com.docgen.repository.GeneratedDocumentRepository;
import com.docgen.repository.TemplateRepository;
import io.minio.MinioClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Service providing dashboard metrics: system overview, API call trends,
 * and system resource usage.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final TemplateRepository templateRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final MeterRegistry meterRegistry;
    private final RedisConnectionFactory redisConnectionFactory;
    private final DataSource dataSource;
    private final MinioClient minioClient;

    public DashboardService(TemplateRepository templateRepository,
                            GeneratedDocumentRepository generatedDocumentRepository,
                            MeterRegistry meterRegistry,
                            RedisConnectionFactory redisConnectionFactory,
                            DataSource dataSource,
                            MinioClient minioClient) {
        this.templateRepository = templateRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.meterRegistry = meterRegistry;
        this.redisConnectionFactory = redisConnectionFactory;
        this.dataSource = dataSource;
        this.minioClient = minioClient;
    }


    public SystemOverviewDTO getSystemOverview() {
        long totalTemplates = templateRepository.count();
        long activeTemplates = templateRepository.countByStatus("ACTIVE");
        long totalApiCalls = getTotalApiCallCount();
        long totalDocuments = generatedDocumentRepository.count();

        SystemOverviewDTO dto = new SystemOverviewDTO(totalTemplates, activeTemplates, totalApiCalls, totalDocuments);
        dto.setCompositeTemplateCount(templateRepository.countByTemplateType("COMPOSITE"));
        return dto;
    }


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


    public SystemResourceDTO getSystemResources() {
        SystemResourceDTO dto = new SystemResourceDTO();
        dto.setJvmMemory(collectJvmMemory());
        dto.setDbPool(collectDbPool());
        dto.setRedisMemory(collectRedisMemory());
        return dto;
    }


    public List<DataSourceHealthDTO> getDataSourceHealth() {
        List<DataSourceHealthDTO> results = new ArrayList<>();
        results.add(checkPostgresHealth());
        results.add(checkRedisHealth());
        results.add(checkMinioHealth());
        return results;
    }


    private long getTotalApiCallCount() {
        double total = 0;
        for (Counter counter : meterRegistry.find("api.request.count").counters()) {
            total += counter.count();
        }
        return (long) total;
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

    private DataSourceHealthDTO checkPostgresHealth() {
        long start = System.nanoTime();
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            return new DataSourceHealthDTO("PostgreSQL", "DATABASE", true, Math.round(ms * 10.0) / 10.0, null);
        } catch (Exception e) {
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            log.warn("PostgreSQL health check failed: {}", e.getMessage());
            return new DataSourceHealthDTO("PostgreSQL", "DATABASE", false, Math.round(ms * 10.0) / 10.0, e.getMessage());
        }
    }

    private DataSourceHealthDTO checkRedisHealth() {
        long start = System.nanoTime();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            boolean reachable = "PONG".equalsIgnoreCase(pong);
            return new DataSourceHealthDTO("Redis", "CACHE", reachable, Math.round(ms * 10.0) / 10.0,
                    reachable ? null : "Unexpected ping response: " + pong);
        } catch (Exception e) {
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            log.warn("Redis health check failed: {}", e.getMessage());
            return new DataSourceHealthDTO("Redis", "CACHE", false, Math.round(ms * 10.0) / 10.0, e.getMessage());
        }
    }

    private DataSourceHealthDTO checkMinioHealth() {
        long start = System.nanoTime();
        try {
            minioClient.listBuckets();
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            return new DataSourceHealthDTO("MinIO", "OBJECT_STORAGE", true, Math.round(ms * 10.0) / 10.0, null);
        } catch (Exception e) {
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            log.warn("MinIO health check failed: {}", e.getMessage());
            return new DataSourceHealthDTO("MinIO", "OBJECT_STORAGE", false, Math.round(ms * 10.0) / 10.0, e.getMessage());
        }
    }
}
