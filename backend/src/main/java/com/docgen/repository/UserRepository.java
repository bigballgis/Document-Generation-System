package com.docgen.repository;

import com.docgen.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Users in a tenant that belong to a team (for reviewer candidate listing).
     */
    List<User> findByTenantIdAndTeamIdOrderByUsernameAsc(Long tenantId, Long teamId);

    boolean existsByTeamId(Long teamId);

    /**
     * Maker-checker teams require every member to have an explicit lane before the mode can be enabled.
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.teamId = :teamId "
            + "AND (u.teamReviewLane IS NULL OR u.teamReviewLane = '')")
    boolean existsByTeamIdWithMissingReviewLane(@Param("teamId") Long teamId);
}
