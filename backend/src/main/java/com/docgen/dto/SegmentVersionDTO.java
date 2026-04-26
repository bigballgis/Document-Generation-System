package com.docgen.dto;

import java.time.Instant;

/**
 * DTO representing a published segment version.
 */
public record SegmentVersionDTO(
        Long id,
        Long templateId,
        String segmentName,
        Integer versionNumber,
        String filePath,
        String segmentType,
        String configSnapshot,
        String comment,
        Long createdBy,
        Instant createdAt
) {}
