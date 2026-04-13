package com.docgen.service;

import com.docgen.entity.DataSource;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.DataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Service that wraps data source calls with Redis caching.
 * <p>
 * Cache key format: {@code datasource:{dataSourceId}:{md5HashOfParams}}
 * <p>
 * Validates: Requirements 14.1-14.7
 */
@Service
public class DataSourceCacheService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceCacheService.class);
    private static final String CACHE_KEY_PREFIX = "datasource:";

    private final RedisTemplate<String, String> redisTemplate;
    private final DataSourceRepository dataSourceRepository;

    public DataSourceCacheService(RedisTemplate<String, String> redisTemplate,
                                  DataSourceRepository dataSourceRepository) {
        this.redisTemplate = redisTemplate;
        this.dataSourceRepository = dataSourceRepository;
    }

    /**
     * Check cache first; on miss, call fetchFunction and cache the result.
     *
     * @param dataSourceId  the data source ID
     * @param parameters    the query parameters (used for cache key hashing)
     * @param fetchFunction supplier that fetches data from the actual data source
     * @return cached or freshly fetched data as a JSON string
     */
    public String getCachedOrFetch(Long dataSourceId, Map<String, Object> parameters,
                                   Supplier<String> fetchFunction) {
        DataSource ds = dataSourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DATASOURCE_NOT_FOUND, "数据源不存在"));

        if (!ds.isCacheEnabled()) {
            log.debug("Cache disabled for dataSource={}, fetching directly", dataSourceId);
            return fetchFunction.get();
        }

        String cacheKey = buildCacheKey(dataSourceId, parameters);

        // Try cache hit
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for key={}", cacheKey);
            return cached;
        }

        // Cache miss — fetch and store
        log.debug("Cache miss for key={}, fetching from data source", cacheKey);
        String result = fetchFunction.get();

        int ttl = ds.getCacheTtl() != null ? ds.getCacheTtl() : 300;
        redisTemplate.opsForValue().set(cacheKey, result, ttl, TimeUnit.SECONDS);
        log.debug("Cached result for key={} with TTL={}s", cacheKey, ttl);

        return result;
    }

    /**
     * Clear all cached entries for a given data source.
     *
     * @param dataSourceId the data source ID
     */
    public void clearCache(Long dataSourceId) {
        String pattern = CACHE_KEY_PREFIX + dataSourceId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} cache entries for dataSource={}", keys.size(), dataSourceId);
        } else {
            log.debug("No cache entries found for dataSource={}", dataSourceId);
        }
    }

    /**
     * Build the Redis cache key: {@code datasource:{dataSourceId}:{md5HashOfParams}}.
     *
     * @param dataSourceId the data source ID
     * @param parameters   the query parameters
     * @return the cache key string
     */
    String buildCacheKey(Long dataSourceId, Map<String, Object> parameters) {
        String paramString = parameters != null ? parameters.toString() : "";
        String hash = md5(paramString);
        return CACHE_KEY_PREFIX + dataSourceId + ":" + hash;
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
