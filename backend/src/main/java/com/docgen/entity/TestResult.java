package com.docgen.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA Entity mapping to the {@code test_results} table.
 * Records the outcome of running a single test case, including
 * the actual result, diff details, and execution timestamp.
 */
@Entity
@Table(name = "test_results")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_case_id", nullable = false)
    private Long testCaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TestStatus status;

    @Column(name = "actual_result_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actualResultJson;

    @Column(name = "diff_details", columnDefinition = "TEXT")
    private String diffDetails;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (this.executedAt == null) {
            this.executedAt = Instant.now();
        }
    }

    // ── Getters and Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTestCaseId() { return testCaseId; }
    public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }

    public TestStatus getStatus() { return status; }
    public void setStatus(TestStatus status) { this.status = status; }

    public String getActualResultJson() { return actualResultJson; }
    public void setActualResultJson(String actualResultJson) { this.actualResultJson = actualResultJson; }

    public String getDiffDetails() { return diffDetails; }
    public void setDiffDetails(String diffDetails) { this.diffDetails = diffDetails; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
