package com.docgen.repository;

import com.docgen.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ApiKey} entities.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    boolean existsByKeyHash(String keyHash);
}
