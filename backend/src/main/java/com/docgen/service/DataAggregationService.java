package com.docgen.service;

import com.docgen.entity.DataSource;
import com.docgen.entity.DataSourceType;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.DataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that fetches data from multiple data sources configured for a template
 * and merges results into a unified data context.
 * <p>
 * Data sources are ordered by priority descending — higher priority wins for conflicting keys.
 * Independent data sources are fetched in parallel using CompletableFuture.
 */
@Service
public class DataAggregationService {

    private static final Logger log = LoggerFactory.getLogger(DataAggregationService.class);

    private final DataSourceRepository dataSourceRepository;
    private final HttpApiDataSourceService httpApiDataSourceService;
    private final DatabaseDataSourceService databaseDataSourceService;
    private final InternalSystemDataSourceService internalSystemDataSourceService;

    public DataAggregationService(DataSourceRepository dataSourceRepository,
                                  HttpApiDataSourceService httpApiDataSourceService,
                                  DatabaseDataSourceService databaseDataSourceService,
                                  InternalSystemDataSourceService internalSystemDataSourceService) {
        this.dataSourceRepository = dataSourceRepository;
        this.httpApiDataSourceService = httpApiDataSourceService;
        this.databaseDataSourceService = databaseDataSourceService;
        this.internalSystemDataSourceService = internalSystemDataSourceService;
    }

    /**
     * Aggregate data from all data sources configured for a template.
     * <p>
     * Data sources are fetched in parallel. Results are merged into a single map
     * where conflicting keys are resolved by priority (higher priority wins).
     * The data sources are already ordered by priority descending from the repository.
     *
     * @param templateId the template ID
     * @param parameters runtime parameters passed to each data source
     * @return merged data context
     */
    public Map<String, Object> aggregateData(Long templateId, Map<String, Object> parameters) {
        List<DataSource> dataSources = dataSourceRepository.findByTemplateIdOrderByPriorityDesc(templateId);

        if (dataSources.isEmpty()) {
            log.info("No data sources configured for template {}", templateId);
            return Collections.emptyMap();
        }

        log.info("Aggregating data from {} data source(s) for template {}", dataSources.size(), templateId);

        // Fetch all data sources in parallel
        Map<DataSource, CompletableFuture<Map<String, Object>>> futures = new LinkedHashMap<>();
        for (DataSource ds : dataSources) {
            futures.put(ds, CompletableFuture.supplyAsync(() -> fetchFromDataSource(ds, parameters)));
        }

        // Wait for all futures and collect results (ordered by priority desc)
        List<FetchResult> results = new ArrayList<>();
        for (Map.Entry<DataSource, CompletableFuture<Map<String, Object>>> entry : futures.entrySet()) {
            DataSource ds = entry.getKey();
            try {
                Map<String, Object> data = entry.getValue().join();
                results.add(new FetchResult(ds, data));
                log.debug("Data source '{}' (priority={}) returned {} fields",
                        ds.getName(), ds.getPriority(), data.size());
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("Data source '{}' (id={}) failed: {}", ds.getName(), ds.getId(), cause.getMessage());
                if (cause instanceof BusinessException be) {
                    throw be;
                }
                throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                        "数据源 '" + ds.getName() + "' 调用失败: " + cause.getMessage(),
                        HttpStatus.BAD_GATEWAY, cause);
            }
        }

        return mergeResults(results);
    }

    /**
     * Fetch data from a single data source based on its type.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> fetchFromDataSource(DataSource ds, Map<String, Object> parameters) {
        DataSourceType type;
        try {
            type = DataSourceType.valueOf(ds.getType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "不支持的数据源类型: " + ds.getType(), HttpStatus.BAD_REQUEST);
        }

        return switch (type) {
            case HTTP_API -> httpApiDataSourceService.fetchData(ds.getConfigJson(), parameters);
            case DATABASE -> {
                List<Map<String, Object>> rows = databaseDataSourceService.fetchData(ds.getConfigJson(), parameters);
                // Flatten single-row result to a map; multi-row results stored under data source name
                if (rows.size() == 1) {
                    yield new LinkedHashMap<>(rows.get(0));
                } else {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put(ds.getName(), rows);
                    yield result;
                }
            }
            case INTERNAL_SYSTEM -> internalSystemDataSourceService.fetchData(ds.getConfigJson(), parameters);
        };
    }

    /**
     * Merge results from multiple data sources. Data sources are already ordered by priority desc,
     * so the first entry has the highest priority. For conflicting keys, higher priority wins.
     */
    Map<String, Object> mergeResults(List<FetchResult> results) {
        // Build merged map: iterate from lowest priority to highest so that higher priority overwrites
        Map<String, Object> merged = new LinkedHashMap<>();
        for (int i = results.size() - 1; i >= 0; i--) {
            FetchResult fr = results.get(i);
            if (fr.data() != null) {
                merged.putAll(fr.data());
            }
        }
        return merged;
    }

    /**
     * Internal record holding a data source and its fetched data.
     */
    record FetchResult(DataSource dataSource, Map<String, Object> data) {}
}
