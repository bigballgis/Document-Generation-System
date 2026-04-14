package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for Assembly_Config JSONB serialization, deserialization and validation.
 * Handles the conversion between {@link AssemblyConfigDTO} and its JSON string
 * representation, and validates business rules before persistence.
 */
@Service
public class AssemblyConfigService {

    private static final Logger log = LoggerFactory.getLogger(AssemblyConfigService.class);

    private final ObjectMapper objectMapper;

    public AssemblyConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
     *   <li>Every segment entry must have a non-null, non-blank filePath (ASSEMBLY_CONFIG_INVALID, 422)</li>
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

        // Check each segment entry has a non-null, non-blank filePath
        for (AssemblySegmentEntry entry : segments) {
            if (entry.getFilePath() == null || entry.getFilePath().isBlank()) {
                throw new BusinessException(ErrorCode.ASSEMBLY_CONFIG_INVALID,
                        "Segment entry missing filePath: " + entry.getName(),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        log.debug("Assembly config validated successfully: {} segments, {} enabled",
                segments.size(),
                segments.stream().filter(AssemblySegmentEntry::isEnabled).count());
    }
}
