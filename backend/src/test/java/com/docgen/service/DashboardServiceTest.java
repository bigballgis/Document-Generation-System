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
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private DataSourceRepository dataSourceRepository;
    @Mock private RedisConnectionFactory redisConnectionFactory;
    @Mock private RedisConnection redisConnection;
    @Mock private RedisServerCommands redisServerCommands;

    private SimpleMeterRegistry meterRegistry;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dashboardService = new DashboardService(
                templateRepository, generatedDocumentRepository,
                dataSourceRepository, meterRegistry, redisConnectionFactory);
    }

    // ── getSystemOverview ──

    @Test
    void getSystemOverview_returnsCounts() {
        when(templateRepository.count()).thenReturn(5L);
        when(templateRepository.countByStatus("ACTIVE")).thenReturn(1L);
        when(generatedDocumentRepository.count()).thenReturn(42L);
        when(templateRepository.countByTemplateType("COMPOSITE")).thenReturn(2L);

        meterRegistry.counter("api.request.count", "method", "GET", "uri", "/api/test", "status", "200")
                .increment(10);

        SystemOverviewDTO overview = dashboardService.getSystemOverview();

        assertEquals(5, overview.getTotalTemplates());
        assertEquals(1, overview.getActiveTemplates());
        assertEquals(10, overview.getTotalApiCalls());
        assertEquals(42, overview.getTotalDocuments());
        assertEquals(2, overview.getCompositeTemplateCount());
    }

    @Test
    void getSystemOverview_noActiveTemplates() {
        when(templateRepository.count()).thenReturn(1L);
        when(templateRepository.countByStatus("ACTIVE")).thenReturn(0L);
        when(generatedDocumentRepository.count()).thenReturn(0L);
        when(templateRepository.countByTemplateType("COMPOSITE")).thenReturn(0L);

        SystemOverviewDTO overview = dashboardService.getSystemOverview();

        assertEquals(1, overview.getTotalTemplates());
        assertEquals(0, overview.getActiveTemplates());
        assertEquals(0, overview.getTotalDocuments());
    }

    // ── getApiCallMetrics ──

    @Test
    void getApiCallMetrics_returnsCorrectBucketCount() {
        List<ApiCallMetricDTO> metrics = dashboardService.getApiCallMetrics(60);
        assertEquals(60, metrics.size());
    }

    @Test
    void getApiCallMetrics_defaultsToSixtyWhenZero() {
        List<ApiCallMetricDTO> metrics = dashboardService.getApiCallMetrics(0);
        assertEquals(60, metrics.size());
    }

    @Test
    void getApiCallMetrics_latestBucketCarriesSnapshot() {
        Timer timer = meterRegistry.timer("api.request.duration", "method", "GET", "uri", "/api/x", "status", "200");
        timer.record(100, TimeUnit.MILLISECONDS);
        timer.record(200, TimeUnit.MILLISECONDS);

        List<ApiCallMetricDTO> metrics = dashboardService.getApiCallMetrics(5);
        assertEquals(5, metrics.size());

        ApiCallMetricDTO last = metrics.get(metrics.size() - 1);
        assertEquals(2, last.getCallCount());
        assertTrue(last.getAvgResponseTimeMs() > 0);
    }

    // ── getDataSourceHealth ──

    @Test
    void getDataSourceHealth_returnsHealthForEachDataSource() {
        DataSource ds1 = new DataSource();
        ds1.setId(1L);
        ds1.setName("api-source");
        ds1.setType("HTTP_API");
        ds1.setConfigJson("{\"url\":\"http://example.com\"}");

        DataSource ds2 = new DataSource();
        ds2.setId(2L);
        ds2.setName("db-source");
        ds2.setType("DATABASE");
        ds2.setConfigJson("{\"host\":\"localhost\"}");

        when(dataSourceRepository.findAll()).thenReturn(List.of(ds1, ds2));

        List<DataSourceHealthDTO> health = dashboardService.getDataSourceHealth();

        assertEquals(2, health.size());
        assertTrue(health.get(0).isReachable());
        assertTrue(health.get(1).isReachable());
    }

    @Test
    void getDataSourceHealth_unknownTypeMarkedUnreachable() {
        DataSource ds = new DataSource();
        ds.setId(3L);
        ds.setName("unknown");
        ds.setType("UNKNOWN_TYPE");
        ds.setConfigJson("{}");

        when(dataSourceRepository.findAll()).thenReturn(List.of(ds));

        List<DataSourceHealthDTO> health = dashboardService.getDataSourceHealth();

        assertEquals(1, health.size());
        assertFalse(health.get(0).isReachable());
        assertNotNull(health.get(0).getLastError());
    }

    // ── getSystemResources ──

    @Test
    void getSystemResources_collectsJvmAndDbPoolAndRedis() {
        Gauge.builder("jvm.memory.used", () -> 500_000_000.0)
                .register(meterRegistry);
        Gauge.builder("jvm.memory.max", () -> 1_000_000_000.0)
                .register(meterRegistry);

        Gauge.builder("hikaricp.connections.active", () -> 3.0).register(meterRegistry);
        Gauge.builder("hikaricp.connections.idle", () -> 7.0).register(meterRegistry);
        Gauge.builder("hikaricp.connections", () -> 10.0).register(meterRegistry);
        Gauge.builder("hikaricp.connections.max", () -> 20.0).register(meterRegistry);

        Properties redisInfo = new Properties();
        redisInfo.setProperty("used_memory", "104857600");
        redisInfo.setProperty("maxmemory", "536870912");

        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.serverCommands()).thenReturn(redisServerCommands);
        when(redisServerCommands.info("memory")).thenReturn(redisInfo);

        SystemResourceDTO resources = dashboardService.getSystemResources();

        assertNotNull(resources.getJvmMemory());
        assertEquals(500_000_000L, resources.getJvmMemory().getUsedBytes());
        assertEquals(1_000_000_000L, resources.getJvmMemory().getMaxBytes());
        assertEquals(50.0, resources.getJvmMemory().getUsagePercent());

        assertNotNull(resources.getDbPool());
        assertEquals(3, resources.getDbPool().getActiveConnections());
        assertEquals(7, resources.getDbPool().getIdleConnections());
        assertEquals(10, resources.getDbPool().getTotalConnections());
        assertEquals(20, resources.getDbPool().getMaxConnections());
        assertEquals(15.0, resources.getDbPool().getUsagePercent());

        assertNotNull(resources.getRedisMemory());
        assertEquals(104857600L, resources.getRedisMemory().getUsedMemoryBytes());
        assertEquals(536870912L, resources.getRedisMemory().getMaxMemoryBytes());
        assertTrue(resources.getRedisMemory().getUsagePercent() > 0);
    }

    @Test
    void getSystemResources_handlesRedisConnectionFailure() {
        Gauge.builder("jvm.memory.used", () -> 100.0).register(meterRegistry);
        Gauge.builder("jvm.memory.max", () -> 200.0).register(meterRegistry);

        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        SystemResourceDTO resources = dashboardService.getSystemResources();

        assertNotNull(resources.getRedisMemory());
        assertEquals(0, resources.getRedisMemory().getUsedMemoryBytes());
        assertEquals(0, resources.getRedisMemory().getMaxMemoryBytes());
    }
}
