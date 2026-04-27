package com.docgen.repository;

import com.docgen.entity.TestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for test result records.
 */
@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findByTestCaseIdOrderByExecutedAtDesc(Long testCaseId);

    Optional<TestResult> findFirstByTestCaseIdOrderByExecutedAtDesc(Long testCaseId);

    /**
     * Paged trial history for one test case. Sort must be supplied via {@link Pageable}
     * (typically {@code executedAt DESC, id DESC}).
     */
    @Query("SELECT r FROM TestResult r WHERE r.testCaseId = :testCaseId")
    Page<TestResult> pageByTestCaseId(@Param("testCaseId") Long testCaseId, Pageable pageable);

    /**
     * One row per test case: latest execution (PostgreSQL DISTINCT ON).
     */
    @Query(value = """
            SELECT DISTINCT ON (test_case_id) *
            FROM test_results
            WHERE test_case_id IN (:ids)
            ORDER BY test_case_id, executed_at DESC, id DESC
            """, nativeQuery = true)
    List<TestResult> findLatestResultsForTestCaseIds(@Param("ids") Collection<Long> ids);
}
