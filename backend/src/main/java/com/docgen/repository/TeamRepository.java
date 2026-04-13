package com.docgen.repository;

import com.docgen.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Team} entities.
 */
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByTenantIdAndName(Long tenantId, String name);

    boolean existsByTenantIdAndName(Long tenantId, String name);

    List<Team> findByTenantId(Long tenantId);
}
