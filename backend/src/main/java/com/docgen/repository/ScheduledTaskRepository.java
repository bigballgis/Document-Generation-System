package com.docgen.repository;

import com.docgen.entity.ScheduledTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for scheduled task records.
 */
@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    List<ScheduledTask> findByTemplateIdOrderByCreatedAtDesc(Long templateId);

    List<ScheduledTask> findByEnabledTrue();
}
