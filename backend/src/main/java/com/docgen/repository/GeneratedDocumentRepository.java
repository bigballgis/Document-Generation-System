package com.docgen.repository;

import com.docgen.entity.GeneratedDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link GeneratedDocument} entities.
 */
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {

    /**
     * Query documents with optional filters: templateId, status, time range.
     * Uses CAST to avoid Hibernate inferring bytea/unknown type for null parameters.
     */
    @Query("SELECT d FROM GeneratedDocument d WHERE "
            + "(CAST(:templateId AS long) IS NULL OR d.templateId = :templateId) "
            + "AND (CAST(:status AS string) IS NULL OR d.status = :status) "
            + "AND (CAST(:startTime AS instant) IS NULL OR d.generatedAt >= :startTime) "
            + "AND (CAST(:endTime AS instant) IS NULL OR d.generatedAt <= :endTime) "
            + "ORDER BY d.generatedAt DESC")
    Page<GeneratedDocument> findByFilters(
            @Param("templateId") Long templateId,
            @Param("status") String status,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable);

    /**
     * Find expired temporary documents for cleanup.
     */
    @Query("SELECT d FROM GeneratedDocument d WHERE d.storageStrategy = 'TEMP' "
            + "AND d.expiresAt IS NOT NULL AND d.expiresAt < :now")
    List<GeneratedDocument> findExpiredTempDocuments(@Param("now") Instant now);

    /**
     * Delete expired temporary documents.
     */
    @Modifying
    @Query("DELETE FROM GeneratedDocument d WHERE d.storageStrategy = 'TEMP' "
            + "AND d.expiresAt IS NOT NULL AND d.expiresAt < :now")
    int deleteExpiredTempDocuments(@Param("now") Instant now);

    List<GeneratedDocument> findByTemplateId(Long templateId);
}
