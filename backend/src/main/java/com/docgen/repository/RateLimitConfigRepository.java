package com.docgen.repository;

import com.docgen.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RateLimitConfig} entities.
 */
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {

    Optional<RateLimitConfig> findByTenantId(Long tenantId);
}
