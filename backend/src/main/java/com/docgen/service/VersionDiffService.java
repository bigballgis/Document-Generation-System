package com.docgen.service;

import com.docgen.dto.VersionDiffResult;
import com.docgen.dto.VersionDiffResult.ChangeSummary;
import com.docgen.dto.VersionDiffResult.DiffEntry;
import com.docgen.dto.VersionDiffResult.DiffEntry.ChangeType;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for comparing two template versions.
 * Detects text content diffs, variable diffs, data source config diffs,
 * expression config diffs, and provides a change summary.
 */
@Service
public class VersionDiffService {

    private static final Logger log = LoggerFactory.getLogger(VersionDiffService.class);

    private final TemplateVersionRepository versionRepository;

    public VersionDiffService(TemplateVersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    /**
     * Compare two versions of a template identified by version numbers.
     *
     * @param templateId the template ID
     * @param versionA   version number of the first (older) version
     * @param versionB   version number of the second (newer) version
     * @return diff result containing all detected differences
     */
    @Transactional(readOnly = true)
    public VersionDiffResult compareVersions(Long templateId, int versionA, int versionB) {
        TemplateVersion verA = versionRepository
                .findByTemplateIdAndVersionNumber(templateId, versionA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_VERSION_NOT_FOUND",
                        "Template version " + versionA + " not found for template " + templateId));

        TemplateVersion verB = versionRepository
                .findByTemplateIdAndVersionNumber(templateId, versionB)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_VERSION_NOT_FOUND",
                        "Template version " + versionB + " not found for template " + templateId));

        Map<String, String> configA = parseConfigJson(verA.getConfigJson());
        Map<String, String> configB = parseConfigJson(verB.getConfigJson());

        List<DiffEntry> textDiffs = diffConfigFields(configA, configB,
                Set.of("name", "description", "outputFormat", "storageStrategy", "status"));
        List<DiffEntry> variableDiffs = diffConfigFields(configA, configB,
                Set.of("async", "reviewRequired"));
        List<DiffEntry> dataSourceDiffs = diffConfigFields(configA, configB,
                Set.of("teamId", "categoryId"));
        List<DiffEntry> expressionDiffs = diffTemplateFilePaths(verA, verB);

        List<DiffEntry> allDiffs = new ArrayList<>();
        allDiffs.addAll(textDiffs);
        allDiffs.addAll(variableDiffs);
        allDiffs.addAll(dataSourceDiffs);
        allDiffs.addAll(expressionDiffs);

        int additions = 0, deletions = 0, modifications = 0;
        for (DiffEntry entry : allDiffs) {
            switch (entry.getChangeType()) {
                case ADDED -> additions++;
                case REMOVED -> deletions++;
                case MODIFIED -> modifications++;
            }
        }

        VersionDiffResult result = new VersionDiffResult();
        result.setTemplateId(templateId);
        result.setVersionA(versionA);
        result.setVersionB(versionB);
        result.setTextDiffs(textDiffs);
        result.setVariableDiffs(variableDiffs);
        result.setDataSourceDiffs(dataSourceDiffs);
        result.setExpressionDiffs(expressionDiffs);
        result.setChangeSummary(new ChangeSummary(
                additions + deletions + modifications,
                additions, deletions, modifications));

        log.info("Version diff completed: templateId={}, v{}→v{}, changes={}",
                templateId, versionA, versionB, result.getChangeSummary().getTotalChanges());
        return result;
    }

    /**
     * Compare specific config fields between two parsed config maps.
     */
    List<DiffEntry> diffConfigFields(Map<String, String> configA,
                                     Map<String, String> configB,
                                     Set<String> fields) {
        List<DiffEntry> diffs = new ArrayList<>();
        for (String field : fields) {
            String valA = configA.get(field);
            String valB = configB.get(field);
            if (valA == null && valB == null) {
                continue;
            }
            if (valA == null) {
                diffs.add(new DiffEntry(ChangeType.ADDED, field, null, valB));
            } else if (valB == null) {
                diffs.add(new DiffEntry(ChangeType.REMOVED, field, valA, null));
            } else if (!valA.equals(valB)) {
                diffs.add(new DiffEntry(ChangeType.MODIFIED, field, valA, valB));
            }
        }
        return diffs;
    }

    /**
     * Compare template file paths between two versions.
     */
    private List<DiffEntry> diffTemplateFilePaths(TemplateVersion verA, TemplateVersion verB) {
        List<DiffEntry> diffs = new ArrayList<>();
        String pathA = verA.getTemplateFilePath();
        String pathB = verB.getTemplateFilePath();
        if (pathA != null && pathB != null && !pathA.equals(pathB)) {
            diffs.add(new DiffEntry(ChangeType.MODIFIED, "templateFilePath", pathA, pathB));
        } else if (pathA == null && pathB != null) {
            diffs.add(new DiffEntry(ChangeType.ADDED, "templateFilePath", null, pathB));
        } else if (pathA != null && pathB == null) {
            diffs.add(new DiffEntry(ChangeType.REMOVED, "templateFilePath", pathA, null));
        }
        return diffs;
    }

    /**
     * Parse a configJson string into a key-value map.
     * Handles the simple JSON format used by TemplateService.buildConfigJson().
     */
    Map<String, String> parseConfigJson(String configJson) {
        Map<String, String> map = new LinkedHashMap<>();
        if (configJson == null || configJson.isBlank()) {
            return map;
        }
        // Remove outer braces
        String content = configJson.trim();
        if (content.startsWith("{")) {
            content = content.substring(1);
        }
        if (content.endsWith("}")) {
            content = content.substring(0, content.length() - 1);
        }

        // Parse key-value pairs
        int i = 0;
        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) i++;
            if (i >= content.length()) break;

            // Expect opening quote for key
            if (content.charAt(i) != '"') { i++; continue; }
            i++; // skip opening quote
            int keyStart = i;
            while (i < content.length() && content.charAt(i) != '"') {
                if (content.charAt(i) == '\\') i++; // skip escaped char
                i++;
            }
            String key = content.substring(keyStart, i);
            i++; // skip closing quote

            // Skip colon
            while (i < content.length() && content.charAt(i) != ':') i++;
            i++; // skip colon

            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) i++;
            if (i >= content.length()) break;

            String value;
            if (content.charAt(i) == '"') {
                // String value
                i++; // skip opening quote
                StringBuilder sb = new StringBuilder();
                while (i < content.length() && content.charAt(i) != '"') {
                    if (content.charAt(i) == '\\' && i + 1 < content.length()) {
                        sb.append(content.charAt(i + 1));
                        i += 2;
                    } else {
                        sb.append(content.charAt(i));
                        i++;
                    }
                }
                value = sb.toString();
                i++; // skip closing quote
            } else {
                // Non-string value (number, boolean, null)
                int valStart = i;
                while (i < content.length() && content.charAt(i) != ',' && content.charAt(i) != '}') {
                    i++;
                }
                value = content.substring(valStart, i).trim();
            }

            map.put(key, value);

            // Skip comma
            while (i < content.length() && content.charAt(i) != ',' && content.charAt(i) != '}') i++;
            if (i < content.length() && content.charAt(i) == ',') i++;
        }
        return map;
    }
}
