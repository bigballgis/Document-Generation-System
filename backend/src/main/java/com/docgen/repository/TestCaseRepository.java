package com.docgen.repository;

import com.docgen.entity.TestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for template test case records.
 */
@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByTemplateIdOrderByCreatedAtDesc(Long templateId);

    Page<TestCase> findByTemplateIdOrderByCreatedAtDesc(Long templateId, Pageable pageable);

    Page<TestCase> findByTemplateIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
            Long templateId, String name, Pageable pageable);
}
