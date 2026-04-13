package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.docgen.util.ParameterResolver;

import java.net.URI;
import java.util.*;

/**
 * Service for fetching data from external HTTP/REST APIs.
 * <p>
 * Supports GET/POST/PUT methods, authentication (API Key header, OAuth Bearer, Basic Auth),
 * configurable timeout (default 5000ms), and retry with exponential backoff.
 */
@Service
public class HttpApiDataSourceService {

    private static final Logger log = LoggerFactory.getLogger(HttpApiDataSourceService.class);

    static final int DEFAULT_TIMEOUT = 5000;
    static final int DEFAULT_RETRY_COUNT = 0;
    static final int DEFAULT_RETRY_INTERVAL = 1000;

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public HttpApiDataSourceService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetch data from an HTTP API using the provided configuration.
     *
     * @param configJson  the data source configJson (with encrypted sensitive fields)
     * @param parameters  additional runtime parameters (can override query params)
     * @return parsed response as a Map
     */
    public Map<String, Object> fetchData(String configJson, Map<String, Object> parameters) {
        Map<String, Object> config = parseConfig(configJson);
        List<Map<String, Object>> paramDefs = ParameterResolver.extractParameterDefs(config);
        Map<String, Object> resolvedParams = ParameterResolver.resolve(paramDefs, parameters);
        RestTemplate restTemplate = buildRestTemplate(config);
        HttpEntity<Object> entity = buildHttpEntity(config, resolvedParams);
        String url = buildUrl(config, resolvedParams);
        HttpMethod method = resolveMethod(config);

        int retryCount = getInt(config, "retryCount", DEFAULT_RETRY_COUNT);
        int retryInterval = getInt(config, "retryInterval", DEFAULT_RETRY_INTERVAL);
        boolean exponentialBackoff = getBoolean(config, "retryExponentialBackoff", false);

        return executeWithRetry(restTemplate, url, method, entity, retryCount, retryInterval, exponentialBackoff);
    }

    /**
     * Test the connection to an HTTP API.
     *
     * @param configJson the data source configJson (with encrypted sensitive fields)
     * @return a result map with "success" (boolean) and "message" (String)
     */
    public Map<String, Object> testConnection(String configJson) {
        try {
            Map<String, Object> config = parseConfig(configJson);
            RestTemplate restTemplate = buildRestTemplate(config);
            HttpEntity<Object> entity = buildHttpEntity(config, Collections.emptyMap());
            String url = buildUrl(config, Collections.emptyMap());
            HttpMethod method = resolveMethod(config);

            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

            return Map.of(
                    "success", true,
                    "message", "连接成功，HTTP " + response.getStatusCode().value()
            );
        } catch (ResourceAccessException e) {
            return Map.of(
                    "success", false,
                    "message", "连接超时或不可达: " + e.getMessage()
            );
        } catch (RestClientResponseException e) {
            return Map.of(
                    "success", false,
                    "message", "HTTP " + e.getStatusCode().value() + ": " + e.getStatusText()
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "message", "连接测试失败: " + e.getMessage()
            );
        }
    }

    // ── Internal helpers ──

    Map<String, Object> parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "数据源配置JSON解析失败: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }
    }

    RestTemplate buildRestTemplate(Map<String, Object> config) {
        int timeout = getInt(config, "timeout", DEFAULT_TIMEOUT);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    HttpEntity<Object> buildHttpEntity(Map<String, Object> config, Map<String, Object> parameters) {
        HttpHeaders headers = new HttpHeaders();

        // Apply custom headers from config
        Object headersObj = config.get("headers");
        if (headersObj instanceof Map<?, ?> headerMap) {
            headerMap.forEach((k, v) -> headers.set(String.valueOf(k), String.valueOf(v)));
        }

        // Apply authentication
        applyAuth(headers, config);

        // Build request body for POST/PUT
        HttpMethod method = resolveMethod(config);
        Object body = null;
        if (method == HttpMethod.POST || method == HttpMethod.PUT) {
            body = parameters != null && !parameters.isEmpty() ? parameters : config.get("body");
        }

        return new HttpEntity<>(body, headers);
    }

    void applyAuth(HttpHeaders headers, Map<String, Object> config) {
        String authType = getString(config, "authType", "NONE");

        switch (authType.toUpperCase()) {
            case "API_KEY" -> {
                String apiKey = decryptField(config, "apiKey");
                if (apiKey != null && !apiKey.isEmpty()) {
                    headers.set("X-API-Key", apiKey);
                }
            }
            case "OAUTH" -> {
                String token = decryptField(config, "token");
                if (token != null && !token.isEmpty()) {
                    headers.setBearerAuth(token);
                }
            }
            case "BASIC" -> {
                String username = getString(config, "username", "");
                String password = decryptField(config, "password");
                if (!username.isEmpty() && password != null) {
                    headers.setBasicAuth(username, password);
                }
            }
            default -> { /* NONE — no auth */ }
        }
    }

    String buildUrl(Map<String, Object> config, Map<String, Object> parameters) {
        String url = getString(config, "url", "");
        if (url.isEmpty()) {
            throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "数据源URL不能为空", HttpStatus.BAD_REQUEST);
        }

        // Replace path parameters: /users/{userId} → /users/123
        url = ParameterResolver.replacePathParameters(url, parameters);

        // Append query params for GET requests
        HttpMethod method = resolveMethod(config);
        if (method == HttpMethod.GET) {
            Map<String, String> queryParams = new LinkedHashMap<>();

            // Params from config
            Object paramsObj = config.get("params");
            if (paramsObj instanceof Map<?, ?> paramMap) {
                paramMap.forEach((k, v) -> queryParams.put(String.valueOf(k), String.valueOf(v)));
            }

            // Override/add from runtime parameters
            if (parameters != null) {
                parameters.forEach((k, v) -> queryParams.put(k, String.valueOf(v)));
            }

            if (!queryParams.isEmpty()) {
                StringBuilder sb = new StringBuilder(url);
                sb.append(url.contains("?") ? "&" : "?");
                boolean first = true;
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!first) sb.append("&");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
                url = sb.toString();
            }
        }

        return url;
    }

    HttpMethod resolveMethod(Map<String, Object> config) {
        String method = getString(config, "method", "GET").toUpperCase();
        return switch (method) {
            case "POST" -> HttpMethod.POST;
            case "PUT" -> HttpMethod.PUT;
            default -> HttpMethod.GET;
        };
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> executeWithRetry(RestTemplate restTemplate, String url,
                                          HttpMethod method, HttpEntity<Object> entity,
                                          int retryCount, int retryInterval,
                                          boolean exponentialBackoff) {
        int attempts = retryCount + 1; // first attempt + retries
        Exception lastException = null;

        for (int i = 0; i < attempts; i++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        URI.create(url), method, entity, String.class);

                String body = response.getBody();
                if (body == null || body.isBlank()) {
                    return Collections.emptyMap();
                }
                return objectMapper.readValue(body, new TypeReference<>() {});
            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("HTTP API call attempt {}/{} timed out: {}", i + 1, attempts, e.getMessage());
            } catch (RestClientResponseException e) {
                lastException = e;
                log.warn("HTTP API call attempt {}/{} returned {}: {}",
                        i + 1, attempts, e.getStatusCode().value(), e.getStatusText());
            } catch (Exception e) {
                lastException = e;
                log.warn("HTTP API call attempt {}/{} failed: {}", i + 1, attempts, e.getMessage());
            }

            // Sleep before retry (not after last attempt)
            if (i < attempts - 1) {
                long sleepMs = exponentialBackoff
                        ? retryInterval * (long) Math.pow(2, i)
                        : retryInterval;
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All attempts exhausted
        String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
        if (lastException instanceof ResourceAccessException) {
            throw new BusinessException(ErrorCode.DATASOURCE_TIMEOUT,
                    "HTTP API调用超时: " + errorMsg, HttpStatus.GATEWAY_TIMEOUT, lastException);
        }
        throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                "HTTP API调用失败: " + errorMsg, HttpStatus.BAD_GATEWAY, lastException);
    }

    // ── Utility methods ──

    private String decryptField(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) return null;
        String strValue = String.valueOf(value);
        if (strValue.isEmpty()) return "";
        try {
            return encryptionService.decrypt(strValue);
        } catch (Exception e) {
            // If decryption fails, treat as plaintext (e.g. during testing)
            log.debug("Could not decrypt field '{}', using raw value", key);
            return strValue;
        }
    }

    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
