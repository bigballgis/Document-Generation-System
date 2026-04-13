package com.docgen.repository;

import com.docgen.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for audit log records.
 * Uses CAST to avoid Hibernate inferring bytea/unknown type for null parameters.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId"
            + " AND (CAST(:action AS string) IS NULL OR a.action = :action)"
            + " AND (CAST(:userId AS long) IS NULL OR a.userId = :userId)"
            + " AND (CAST(:startTime AS instant) IS NULL OR a.createdAt >= :startTime)"
            + " AND (CAST(:endTime AS instant) IS NULL OR a.createdAt <= :endTime)"
            + " ORDER BY a.createdAt DESC")
    Page<AuditLog> findByFilters(
            @Param("tenantId") Long tenantId,
            @Param("action") String action,
            @Param("userId") Long userId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId"
            + " AND (CAST(:action AS string) IS NULL OR a.action = :action)"
            + " AND (CAST(:userId AS long) IS NULL OR a.userId = :userId)"
            + " AND (CAST(:startTime AS instant) IS NULL OR a.createdAt >= :startTime)"
            + " AND (CAST(:endTime AS instant) IS NULL OR a.createdAt <= :endTime)"
            + " ORDER BY a.createdAt DESC")
    List<AuditLog> findAllByFilters(
            @Param("tenantId") Long tenantId,
            @Param("action") String action,
            @Param("userId") Long userId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
