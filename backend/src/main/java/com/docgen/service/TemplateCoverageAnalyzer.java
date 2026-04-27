package com.docgen.service;

import com.docgen.dto.PlaceholderInfo;
import com.docgen.dto.UncoveredItem;
import com.docgen.entity.ParameterDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Pure branch / loop / parameter coverage analysis for placeholder-backed templates.
 * Used by {@link CoverageCheckService} and {@link ScenarioReadinessService}.
 */
@Component
public class TemplateCoverageAnalyzer {

    /**
     * Result of analyzing conditions, loops, parameters against one or more test data maps.
     */
    public static final class CoverageAnalysisResult {
        private final double branchCoverage;
        private final double loopCoverage;
        private final double parameterCoverage;
        private final double overallCoverage;
        private final int totalBranches;
        private final int coveredBranches;
        private final int totalLoopScenarios;
        private final int coveredLoopScenarios;
        private final int totalParameters;
        private final int coveredParameters;
        private final List<UncoveredItem> uncoveredItems;

        public CoverageAnalysisResult(double branchCoverage, double loopCoverage, double parameterCoverage,
                                      double overallCoverage,
                                      int totalBranches, int coveredBranches,
                                      int totalLoopScenarios, int coveredLoopScenarios,
                                      int totalParameters, int coveredParameters,
                                      List<UncoveredItem> uncoveredItems) {
            this.branchCoverage = branchCoverage;
            this.loopCoverage = loopCoverage;
            this.parameterCoverage = parameterCoverage;
            this.overallCoverage = overallCoverage;
            this.totalBranches = totalBranches;
            this.coveredBranches = coveredBranches;
            this.totalLoopScenarios = totalLoopScenarios;
            this.coveredLoopScenarios = coveredLoopScenarios;
            this.totalParameters = totalParameters;
            this.coveredParameters = coveredParameters;
            this.uncoveredItems = uncoveredItems;
        }

        public double getBranchCoverage() {
            return branchCoverage;
        }

        public double getLoopCoverage() {
            return loopCoverage;
        }

        public double getParameterCoverage() {
            return parameterCoverage;
        }

        public double getOverallCoverage() {
            return overallCoverage;
        }

        public int getTotalBranches() {
            return totalBranches;
        }

        public int getCoveredBranches() {
            return coveredBranches;
        }

        public int getTotalLoopScenarios() {
            return totalLoopScenarios;
        }

        public int getCoveredLoopScenarios() {
            return coveredLoopScenarios;
        }

        public int getTotalParameters() {
            return totalParameters;
        }

        public int getCoveredParameters() {
            return coveredParameters;
        }

        public List<UncoveredItem> getUncoveredItems() {
            return uncoveredItems;
        }
    }

    public CoverageAnalysisResult analyze(List<PlaceholderInfo> conditions,
                                          List<PlaceholderInfo> loops,
                                          List<ParameterDefinition> params,
                                          List<Map<String, Object>> testDataList) {
        double branchCov = computeBranchCoverage(conditions, testDataList);
        double loopCov = computeLoopCoverage(loops, testDataList);
        double paramCov = computeParameterCoverage(params, testDataList);
        double overall = computeOverallCoverage(branchCov, loopCov, paramCov,
                conditions.size(), loops.size(), params.size());

        int coveredBranchScenarios = countCoveredBranchScenarios(conditions, testDataList);
        int coveredLoopScenarios = countCoveredLoopScenarios(loops, testDataList);
        int coveredParams = countCoveredParameters(params, testDataList);

        List<UncoveredItem> uncovered = findUncoveredItems(conditions, loops, params, testDataList);

        return new CoverageAnalysisResult(
                branchCov,
                loopCov,
                paramCov,
                overall,
                conditions.size() * 2,
                coveredBranchScenarios,
                loops.size() * 2,
                coveredLoopScenarios,
                params.size(),
                coveredParams,
                uncovered
        );
    }

    public double computeBranchCoverage(List<PlaceholderInfo> conditions, List<Map<String, Object>> testDataList) {
        if (conditions.isEmpty()) {
            return 0.0;
        }
        int totalScenarios = conditions.size() * 2;
        int covered = countCoveredBranchScenarios(conditions, testDataList);
        return (covered * 100.0) / totalScenarios;
    }

    public double computeLoopCoverage(List<PlaceholderInfo> loops, List<Map<String, Object>> testDataList) {
        if (loops.isEmpty()) {
            return 0.0;
        }
        int totalScenarios = loops.size() * 2;
        int covered = countCoveredLoopScenarios(loops, testDataList);
        return (covered * 100.0) / totalScenarios;
    }

    public double computeParameterCoverage(List<ParameterDefinition> params, List<Map<String, Object>> testDataList) {
        if (params.isEmpty()) {
            return 0.0;
        }
        int covered = countCoveredParameters(params, testDataList);
        return (covered * 100.0) / params.size();
    }

    /**
     * Equal-weighted average of applicable dimensions; dimensions with zero items are excluded.
     */
    public double computeOverallCoverage(double branchCov, double loopCov, double paramCov,
                                         int conditionCount, int loopCount, int paramCount) {
        double sum = 0.0;
        int dimensions = 0;
        if (conditionCount > 0) {
            sum += branchCov;
            dimensions++;
        }
        if (loopCount > 0) {
            sum += loopCov;
            dimensions++;
        }
        if (paramCount > 0) {
            sum += paramCov;
            dimensions++;
        }
        return dimensions == 0 ? 100.0 : sum / dimensions;
    }

    public void collectConditionsAndLoops(List<PlaceholderInfo> placeholders,
                                          List<PlaceholderInfo> conditions,
                                          List<PlaceholderInfo> loops) {
        for (PlaceholderInfo ph : placeholders) {
            if ("CONDITION".equals(ph.type())) {
                conditions.add(ph);
            } else if ("LOOP".equals(ph.type())) {
                loops.add(ph);
            }
            if (ph.children() != null && !ph.children().isEmpty()) {
                collectConditionsAndLoops(ph.children(), conditions, loops);
            }
        }
    }

    private int countCoveredBranchScenarios(List<PlaceholderInfo> conditions, List<Map<String, Object>> testDataList) {
        int covered = 0;
        for (PlaceholderInfo cond : conditions) {
            boolean seenTrue = false;
            boolean seenFalse = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, cond.name());
                if (isTruthy(val)) {
                    seenTrue = true;
                } else {
                    seenFalse = true;
                }
                if (seenTrue && seenFalse) {
                    break;
                }
            }
            if (seenTrue) {
                covered++;
            }
            if (seenFalse) {
                covered++;
            }
        }
        return covered;
    }

    private int countCoveredLoopScenarios(List<PlaceholderInfo> loops, List<Map<String, Object>> testDataList) {
        int covered = 0;
        for (PlaceholderInfo loop : loops) {
            boolean seenEmpty = false;
            boolean seenNonEmpty = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, loop.name());
                if (val instanceof List<?> list) {
                    if (list.isEmpty()) {
                        seenEmpty = true;
                    } else {
                        seenNonEmpty = true;
                    }
                } else if (val == null) {
                    seenEmpty = true;
                }
                if (seenEmpty && seenNonEmpty) {
                    break;
                }
            }
            if (seenEmpty) {
                covered++;
            }
            if (seenNonEmpty) {
                covered++;
            }
        }
        return covered;
    }

    private int countCoveredParameters(List<ParameterDefinition> params, List<Map<String, Object>> testDataList) {
        int covered = 0;
        for (ParameterDefinition param : params) {
            if ("DERIVED".equals(param.getParameterType())) {
                covered++;
                continue;
            }
            boolean hasNonNull = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, param.getName());
                if (val != null) {
                    hasNonNull = true;
                    break;
                }
            }
            if (hasNonNull) {
                covered++;
            }
        }
        return covered;
    }

    private List<UncoveredItem> findUncoveredItems(List<PlaceholderInfo> conditions,
                                                   List<PlaceholderInfo> loops,
                                                   List<ParameterDefinition> params,
                                                   List<Map<String, Object>> testDataList) {
        List<UncoveredItem> items = new ArrayList<>();

        for (PlaceholderInfo cond : conditions) {
            boolean seenTrue = false;
            boolean seenFalse = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, cond.name());
                if (isTruthy(val)) {
                    seenTrue = true;
                } else {
                    seenFalse = true;
                }
            }
            if (!seenTrue) {
                items.add(new UncoveredItem("BRANCH", cond.name(), "true"));
            }
            if (!seenFalse) {
                items.add(new UncoveredItem("BRANCH", cond.name(), "false"));
            }
        }

        for (PlaceholderInfo loop : loops) {
            boolean seenEmpty = false;
            boolean seenNonEmpty = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, loop.name());
                if (val instanceof List<?> list) {
                    if (list.isEmpty()) {
                        seenEmpty = true;
                    } else {
                        seenNonEmpty = true;
                    }
                } else if (val == null) {
                    seenEmpty = true;
                }
            }
            if (!seenEmpty) {
                items.add(new UncoveredItem("LOOP", loop.name(), "empty"));
            }
            if (!seenNonEmpty) {
                items.add(new UncoveredItem("LOOP", loop.name(), "non-empty"));
            }
        }

        for (ParameterDefinition param : params) {
            if ("DERIVED".equals(param.getParameterType())) {
                continue;
            }
            boolean hasNonNull = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, param.getName());
                if (val != null) {
                    hasNonNull = true;
                    break;
                }
            }
            if (!hasNonNull) {
                items.add(new UncoveredItem("PARAMETER", param.getName(), "null"));
            }
        }

        return items;
    }

    private Object resolveValue(Map<String, Object> data, String name) {
        if (data == null) {
            return null;
        }
        if (data.containsKey(name)) {
            return data.get(name);
        }
        String[] segments = name.split("\\.");
        Object current = data;
        for (String seg : segments) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(seg);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean isTruthy(Object val) {
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (val instanceof String s) {
            return !s.isEmpty();
        }
        if (val instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        return true;
    }
}
