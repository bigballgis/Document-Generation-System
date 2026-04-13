package com.docgen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

/**
 * Redis configuration for token storage (refresh tokens, blacklist, etc.).
 */
@Configuration
public class RedisConfig {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Store a refresh token in Redis with a TTL.
     */
    public static void storeRefreshToken(RedisTemplate<String, String> redisTemplate,
                                         Long userId,
                                         String refreshToken,
                                         long expirationMs) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken, expirationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Retrieve the stored refresh token for a user.
     */
    public static String getRefreshToken(RedisTemplate<String, String> redisTemplate, Long userId) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
    }

    /**
     * Delete the refresh token for a user (logout / token rotation).
     */
    public static void deleteRefreshToken(RedisTemplate<String, String> redisTemplate, Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }
}
