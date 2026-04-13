package com.docgen.repository;

import com.docgen.entity.AsyncTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AsyncTask} entities.
 */
public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long> {

    Optional<AsyncTask> findByTaskId(String taskId);
}
