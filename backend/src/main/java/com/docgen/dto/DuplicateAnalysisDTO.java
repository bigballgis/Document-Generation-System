package com.docgen.dto;

import java.util.List;

/**
 * DTO for segment duplicate analysis results.
 * Contains pairs of segments with high content similarity.
 */
public class DuplicateAnalysisDTO {

    private List<DuplicatePair> duplicates;

    public DuplicateAnalysisDTO() {}

    public List<DuplicatePair> getDuplicates() { return duplicates; }
    public void setDuplicates(List<DuplicatePair> duplicates) { this.duplicates = duplicates; }

    /**
     * A pair of segments identified as having high content similarity.
     */
    public static class DuplicatePair {

        private Long segmentIdA;
        private Long segmentIdB;
        private String segmentNameA;
        private String segmentNameB;
        private double similarityPercent;

        public DuplicatePair() {}

        public Long getSegmentIdA() { return segmentIdA; }
        public void setSegmentIdA(Long segmentIdA) { this.segmentIdA = segmentIdA; }

        public Long getSegmentIdB() { return segmentIdB; }
        public void setSegmentIdB(Long segmentIdB) { this.segmentIdB = segmentIdB; }

        public String getSegmentNameA() { return segmentNameA; }
        public void setSegmentNameA(String segmentNameA) { this.segmentNameA = segmentNameA; }

        public String getSegmentNameB() { return segmentNameB; }
        public void setSegmentNameB(String segmentNameB) { this.segmentNameB = segmentNameB; }

        public double getSimilarityPercent() { return similarityPercent; }
        public void setSimilarityPercent(double similarityPercent) { this.similarityPercent = similarityPercent; }
    }
}
