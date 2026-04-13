package com.docgen.repository;

import com.docgen.entity.TaskExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for task execution records.
 */
@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    Page<TaskExecution> findByScheduledTaskIdOrderByExecutedAtDesc(Long scheduledTaskId, Pageable pageable);

    Optional<TaskExecution> findTopByScheduledTaskIdAndStatusOrderByExecutedAtDesc(Long scheduledTaskId, String status);
}
