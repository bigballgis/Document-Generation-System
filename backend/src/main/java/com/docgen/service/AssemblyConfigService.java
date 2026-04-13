package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for Assembly_Config JSONB serialization, deserialization and validation.
 * Handles the conversion between {@link AssemblyConfigDTO} and its JSON string
 * representation, and validates business rules before persistence.
 */
@Service
public class AssemblyConfigService {

    private static final Logger log = LoggerFactory.getLogger(AssemblyConfigService.class);

    private final ObjectMapper objectMapper;
    private final SegmentRepository segmentRepository;

    public AssemblyConfigService(ObjectMapper objectMapper,
                                 SegmentRepository segmentRepository) {
        this.objectMapper = objectMapper;
        this.segmentRepository = segmentRepository;
    }

    /**
     * Serialize an {@link AssemblyConfigDTO} to its JSON string representation.
     *
     * @param config the assembly configuration DTO
     * @return JSON string
     */
    public String serialize(AssemblyConfigDTO config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AssemblyConfigDTO", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to serialize assembly config", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Deserialize a JSON string into an {@link AssemblyConfigDTO}.
     *
     * @param json the JSON string
     * @return the deserialized assembly configuration DTO
     */
    public AssemblyConfigDTO deserialize(String json) {
        try {
            return objectMapper.readValue(json, AssemblyConfigDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize AssemblyConfigDTO from JSON", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Failed to deserialize assembly config", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Validate an {@link AssemblyConfigDTO} against business rules.
     * <ul>
     *   <li>At least one segment must be enabled (COMPOSITE_TEMPLATE_EMPTY, 422)</li>
     *   <li>All referenced segment IDs must exist in the database (SEGMENT_NOT_FOUND, 422)</li>
     * </ul>
     *
     * @param config the assembly configuration to validate
     * @throws BusinessException if validation fails
     */
    public void validate(AssemblyConfigDTO config) {
        List<AssemblySegmentEntry> segments = config.getSegments();

        // Check at least one enabled segment
        boolean hasEnabled = segments != null && segments.stream()
                .anyMatch(AssemblySegmentEntry::isEnabled);
        if (!hasEnabled) {
            throw new BusinessException(ErrorCode.COMPOSITE_TEMPLATE_EMPTY,
                    "Assembly config must contain at least one enabled segment",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Collect all segment IDs referenced in the config
        Set<Long> referencedIds = segments.stream()
                .map(AssemblySegmentEntry::getSegmentId)
                .collect(Collectors.toSet());

        // Find which IDs actually exist in the database
        Set<Long> existingIds = segmentRepository.findAllById(referencedIds).stream()
                .map(segment -> segment.getId())
                .collect(Collectors.toSet());

        // Determine missing IDs
        Set<Long> missingIds = referencedIds.stream()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toSet());

        if (!missingIds.isEmpty()) {
            log.warn("Assembly config references non-existent segment IDs: {}", missingIds);
            throw new BusinessException(ErrorCode.SEGMENT_NOT_FOUND,
                    "Segment IDs not found: " + missingIds,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        log.debug("Assembly config validated successfully: {} segments, {} enabled",
                segments.size(),
                segments.stream().filter(AssemblySegmentEntry::isEnabled).count());
    }
}
