package com.docgen.repository;

import com.docgen.entity.AsyncTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AsyncTask} entities.
 */
public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long> {

    Optional<AsyncTask> findByTaskId(String taskId);

    /**
     * Query async tasks with optional filters: status, templateId, tenantId.
     * Uses CAST to avoid Hibernate inferring bytea/unknown type for null parameters.
     */
    @Query("SELECT t FROM AsyncTask t WHERE "
            + "t.tenantId = :tenantId "
            + "AND (CAST(:status AS string) IS NULL OR t.status = :status) "
            + "AND (CAST(:templateId AS long) IS NULL OR t.templateId = :templateId) "
            + "ORDER BY t.createdAt DESC")
    Page<AsyncTask> findByFilters(
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("templateId") Long templateId,
            Pageable pageable);
}
