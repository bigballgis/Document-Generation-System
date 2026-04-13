package com.docgen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for resolving Segment data scopes.
 * <p>
 * Maps a subset of the global data context to a segment's local variable namespace
 * based on the DataScope configuration in Assembly_Config.
 * <p>
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.6
 */
@Service
public class SegmentDataScopeService {

    private static final Logger log = LoggerFactory.getLogger(SegmentDataScopeService.class);

    /**
     * Resolve the data scope for a segment.
     * <p>
     * If dataScope is null or empty, returns globalData as-is (backward compatible).
     * Otherwise, creates a new map containing only the mapped keys:
     * localKey → globalData.get(globalKey).
     * If a globalKey doesn't exist in globalData, logs a warning and sets the value to null.
     *
     * @param globalData the full global data context from the Composite_Template
     * @param dataScope  the data scope mapping (localKey → globalKey), may be null
     * @return the resolved data map for the segment
     */
    public Map<String, Object> resolveDataScope(Map<String, Object> globalData, Map<String, String> dataScope) {
        if (dataScope == null || dataScope.isEmpty()) {
            log.debug("No DataScope configured, passing full global data context");
            return globalData;
        }

        Map<String, Object> scopedData = new HashMap<>();

        for (Map.Entry<String, String> entry : dataScope.entrySet()) {
            String localKey = entry.getKey();
            String globalKey = entry.getValue();

            if (globalData.containsKey(globalKey)) {
                scopedData.put(localKey, globalData.get(globalKey));
            } else {
                log.warn("DataScope mapping: global variable '{}' not found in global data context, setting local variable '{}' to null",
                        globalKey, localKey);
                scopedData.put(localKey, null);
            }
        }

        return scopedData;
    }
}
