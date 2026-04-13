package com.docgen.repository;

import com.docgen.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for test result records.
 */
@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findByTestCaseIdOrderByExecutedAtDesc(Long testCaseId);

    Optional<TestResult> findFirstByTestCaseIdOrderByExecutedAtDesc(Long testCaseId);
}
