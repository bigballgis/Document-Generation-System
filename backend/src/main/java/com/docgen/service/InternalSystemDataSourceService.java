package com.docgen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for fetching data from internal system APIs.
 * <p>
 * Internal systems are identified by a {@code serviceName} field in the config.
 * Under the hood this delegates to {@link HttpApiDataSourceService} since
 * internal systems are also called via HTTP — the key difference is the
 * service-name based identification and logging.
 */
@Service
public class InternalSystemDataSourceService {

    private static final Logger log = LoggerFactory.getLogger(InternalSystemDataSourceService.class);

    private final HttpApiDataSourceService httpApiDataSourceService;

    public InternalSystemDataSourceService(HttpApiDataSourceService httpApiDataSourceService) {
        this.httpApiDataSourceService = httpApiDataSourceService;
    }

    /**
     * Fetch data from an internal system API.
     *
     * @param configJson  the data source configJson (contains serviceName, url, auth, timeout, retry settings)
     * @param parameters  additional runtime parameters
     * @return parsed response as a Map
     */
    public Map<String, Object> fetchData(String configJson, Map<String, Object> parameters) {
        String serviceName = extractServiceName(configJson);
        log.info("Fetching data from internal system: serviceName={}", serviceName);
        try {
            Map<String, Object> result = httpApiDataSourceService.fetchData(configJson, parameters);
            log.info("Successfully fetched data from internal system: serviceName={}", serviceName);
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch data from internal system: serviceName={}, error={}", serviceName, e.getMessage());
            throw e;
        }
    }

    /**
     * Test the connection to an internal system API.
     *
     * @param configJson the data source configJson
     * @return a result map with "success" (boolean) and "message" (String)
     */
    public Map<String, Object> testConnection(String configJson) {
        String serviceName = extractServiceName(configJson);
        log.info("Testing connection to internal system: serviceName={}", serviceName);
        Map<String, Object> result = httpApiDataSourceService.testConnection(configJson);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (success) {
            log.info("Connection test succeeded for internal system: serviceName={}", serviceName);
        } else {
            log.warn("Connection test failed for internal system: serviceName={}, message={}",
                    serviceName, result.get("message"));
        }
        return result;
    }

    /**
     * Extract the serviceName from configJson for logging/identification.
     * Returns "unknown" if not present or parsing fails.
     */
    private String extractServiceName(String configJson) {
        if (configJson == null) return "unknown";
        try {
            Map<String, Object> config = httpApiDataSourceService.parseConfig(configJson);
            Object name = config.get("serviceName");
            return name != null ? String.valueOf(name) : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
