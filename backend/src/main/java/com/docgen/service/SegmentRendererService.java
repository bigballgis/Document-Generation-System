package com.docgen.service;

import com.docgen.dto.SegmentRenderResult;
import com.docgen.entity.Segment;
import com.docgen.entity.SegmentVersion;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.SegmentVersionRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for rendering individual Segments via the Docxtemplater /render endpoint.
 * Supports version locking and partial failure mode (renderSegmentSafe).
 *
 * <p>Validates: Requirements 7.1, 7.2, 7.7, 8.6, 8.11</p>
 */
@Service
public class SegmentRendererService {

    private static final Logger log = LoggerFactory.getLogger(SegmentRendererService.class);

    private final RestTemplate restTemplate;
    private final CircuitBreaker docxtemplaterCb;
    private final SegmentRepository segmentRepository;
    private final SegmentVersionRepository segmentVersionRepository;
    private final MinioClient minioClient;

    @Value("${docxtemplater.service-url:http://localhost:3000}")
    private String docxtemplaterServiceUrl;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    public SegmentRendererService(RestTemplate restTemplate,
                                  CircuitBreaker docxtemplaterCircuitBreaker,
                                  SegmentRepository segmentRepository,
                                  SegmentVersionRepository segmentVersionRepository,
                                  MinioClient minioClient) {
        this.restTemplate = restTemplate;
        this.docxtemplaterCb = docxtemplaterCircuitBreaker;
        this.segmentRepository = segmentRepository;
        this.segmentVersionRepository = segmentVersionRepository;
        this.minioClient = minioClient;
    }

    /**
     * Render a single Segment via the Docxtemplater /render endpoint, protected by CircuitBreaker.
     * If lockedVersion is non-null, the specific version's .docx file is fetched from MinIO.
     *
     * @param segmentId     the segment ID
     * @param lockedVersion the locked version number (null = use current segment file)
     * @param data          the data context for rendering
     * @return rendered .docx bytes
     */
    public byte[] renderSegment(Long segmentId, Integer lockedVersion, Map<String, Object> data) {
        String filePath = resolveFilePath(segmentId, lockedVersion);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("templatePath", filePath);
        requestBody.put("data", data != null ? data : Map.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            return docxtemplaterCb.executeSupplier(() -> {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        docxtemplaterServiceUrl + "/render",
                        HttpMethod.POST, entity, byte[].class);

                if (response.getBody() == null || response.getBody().length == 0) {
                    throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                            "Segment render returned empty result for segmentId=" + segmentId,
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return response.getBody();
            });
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Docxtemplater render call failed for segment {}: {}", segmentId, e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                    "Segment render service call failed: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("Segment rendering failed for segment {}: {}", segmentId, e.getMessage());
            throw new BusinessException(ErrorCode.GENERATE_RENDER_FAILED,
                    "Segment rendering failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * Render a single Segment safely — catches all exceptions and returns a
     * {@link SegmentRenderResult} with error information (partial failure mode).
     *
     * @param segmentId     the segment ID
     * @param lockedVersion the locked version number (null = use current segment file)
     * @param data          the data context for rendering
     * @return render result with timing and error info
     */
    public SegmentRenderResult renderSegmentSafe(Long segmentId, Integer lockedVersion, Map<String, Object> data) {
        SegmentRenderResult result = new SegmentRenderResult();
        result.setSegmentId(segmentId);

        // Resolve segment name for the result
        segmentRepository.findById(segmentId).ifPresent(s -> result.setSegmentName(s.getName()));

        long start = System.currentTimeMillis();
        try {
            byte[] rendered = renderSegment(segmentId, lockedVersion, data);
            long elapsed = System.currentTimeMillis() - start;
            result.setSuccess(true);
            result.setRenderTimeMs(elapsed);
            result.setRenderedBytes(rendered);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Segment {} render failed (partial failure mode): {}", segmentId, e.getMessage());
            result.setSuccess(false);
            result.setRenderTimeMs(elapsed);
            result.setErrorMessage(e.getMessage());
        }
        return result;
    }

    /**
     * Resolve the file path for a segment, considering version locking.
     */
    String resolveFilePath(Long segmentId, Integer lockedVersion) {
        if (lockedVersion != null) {
            SegmentVersion version = segmentVersionRepository
                    .findBySegmentIdAndVersionNumber(segmentId, lockedVersion)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SEGMENT_VERSION_FILE_MISSING,
                            "Segment version not found: segmentId=" + segmentId + ", version=" + lockedVersion,
                            HttpStatus.UNPROCESSABLE_ENTITY));
            return version.getFilePath();
        }

        Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEGMENT_NOT_FOUND,
                        "Segment not found: " + segmentId, HttpStatus.UNPROCESSABLE_ENTITY));
        return segment.getFilePath();
    }
}
