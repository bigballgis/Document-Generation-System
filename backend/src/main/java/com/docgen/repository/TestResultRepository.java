package com.docgen.repository;

import com.docgen.entity.TestResult;
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
