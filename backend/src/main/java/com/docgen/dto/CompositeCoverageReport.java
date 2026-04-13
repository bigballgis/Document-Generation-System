package com.docgen.dto;

import java.util.List;

/**
 * Coverage report for a Composite_Template.
 * Contains overall coverage and per-segment coverage breakdown.
 */
public class CompositeCoverageReport {

    private double overallCoveragePercent;
    private List<SegmentCoverageEntry> segmentCoverages;

    public CompositeCoverageReport() {}

    public double getOverallCoveragePercent() { return overallCoveragePercent; }
    public void setOverallCoveragePercent(double overallCoveragePercent) { this.overallCoveragePercent = overallCoveragePercent; }

    public List<SegmentCoverageEntry> getSegmentCoverages() { return segmentCoverages; }
    public void setSegmentCoverages(List<SegmentCoverageEntry> segmentCoverages) { this.segmentCoverages = segmentCoverages; }

    /**
     * Coverage entry for a single segment within a composite coverage report.
     */
    public static class SegmentCoverageEntry {

        private Long segmentId;
        private String segmentName;
        private int totalVariables;
        private int boundVariables;
        private double coveragePercent;

        public SegmentCoverageEntry() {}

        public Long getSegmentId() { return segmentId; }
        public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

        public String getSegmentName() { return segmentName; }
        public void setSegmentName(String segmentName) { this.segmentName = segmentName; }

        public int getTotalVariables() { return totalVariables; }
        public void setTotalVariables(int totalVariables) { this.totalVariables = totalVariables; }

        public int getBoundVariables() { return boundVariables; }
        public void setBoundVariables(int boundVariables) { this.boundVariables = boundVariables; }

        public double getCoveragePercent() { return coveragePercent; }
        public void setCoveragePercent(double coveragePercent) { this.coveragePercent = coveragePercent; }
    }
}
