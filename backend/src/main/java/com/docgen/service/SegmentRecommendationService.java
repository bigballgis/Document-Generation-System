package com.docgen.service;

import com.docgen.dto.*;
import com.docgen.entity.Segment;
import com.docgen.entity.Template;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.SegmentRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service providing segment recommendations and duplicate analysis.
 * Also manages preset and custom segment templates.
 */
@Service
public class SegmentRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(SegmentRecommendationService.class);

    private final SegmentRepository segmentRepository;
    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docgen}")
    private String bucketName;

    /**
     * Mapping of segment types to recommended companion types.
     */
    private static final Map<String, List<String>> TYPE_RECOMMENDATIONS = Map.of(
            "COVER", List.of("TOC", "CHAPTER"),
            "TOC", List.of("CHAPTER", "APPENDIX"),
            "CHAPTER", List.of("TABLE", "APPENDIX", "SIGNATURE"),
            "TABLE", List.of("CHAPTER", "APPENDIX"),
            "SIGNATURE", List.of("LEGAL"),
            "LEGAL", List.of("SIGNATURE", "APPENDIX"),
            "APPENDIX", List.of("LEGAL", "SIGNATURE")
    );

    /**
     * Preset segment templates available for one-click creation.
     */
    private static final List<SegmentTemplateDTO> PRESET_TEMPLATES;

    static {
        PRESET_TEMPLATES = new ArrayList<>();
        PRESET_TEMPLATES.add(createPreset("封面页", "标准文档封面页模板", "COVER"));
        PRESET_TEMPLATES.add(createPreset("目录页", "自动生成目录页模板", "TOC"));
        PRESET_TEMPLATES.add(createPreset("章节标题", "标准章节标题模板", "CHAPTER"));
        PRESET_TEMPLATES.add(createPreset("表格数据页", "数据表格展示模板", "TABLE"));
        PRESET_TEMPLATES.add(createPreset("签名页", "签名确认页模板", "SIGNATURE"));
        PRESET_TEMPLATES.add(createPreset("法律声明页", "法律声明与免责条款模板", "LEGAL"));
        PRESET_TEMPLATES.add(createPreset("附录页", "附录内容模板", "APPENDIX"));
    }

    private static SegmentTemplateDTO createPreset(String name, String description, String segmentType) {
        SegmentTemplateDTO dto = new SegmentTemplateDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setSegmentType(segmentType);
        dto.setPreset(true);
        return dto;
    }

    public SegmentRecommendationService(SegmentRepository segmentRepository,
                                        TemplateRepository templateRepository,
                                        ObjectMapper objectMapper,
                                        MinioClient minioClient) {
        this.segmentRepository = segmentRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
        this.minioClient = minioClient;
    }

    /**
     * Recommend segments for a composite template based on existing segment type tags.
     */
    @Transactional(readOnly = true)
    public List<SegmentRecommendationDTO> recommendSegments(Long compositeTemplateId) {
        Template template = templateRepository.findById(compositeTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "组合模板不存在"));

        // Parse assembly config to get current segment IDs
        Set<Long> currentSegmentIds = new HashSet<>();
        Set<String> currentTypes = new HashSet<>();
        AssemblyConfigDTO config = parseAssemblyConfig(template.getAssemblyConfig());
        if (config != null && config.getSegments() != null) {
            for (AssemblySegmentEntry entry : config.getSegments()) {
                currentSegmentIds.add(entry.getSegmentId());
                segmentRepository.findById(entry.getSegmentId())
                        .ifPresent(seg -> {
                            if (seg.getSegmentType() != null) {
                                currentTypes.add(seg.getSegmentType());
                            }
                        });
            }
        }

        // Collect recommended types based on current types
        Set<String> recommendedTypes = new HashSet<>();
        for (String type : currentTypes) {
            List<String> companions = TYPE_RECOMMENDATIONS.get(type);
            if (companions != null) {
                for (String companion : companions) {
                    if (!currentTypes.contains(companion)) {
                        recommendedTypes.add(companion);
                    }
                }
            }
        }

        // Find segments matching recommended types in the same tenant
        Long tenantId = TenantContext.getCurrentTenantId();
        List<SegmentRecommendationDTO> recommendations = new ArrayList<>();
        for (String recType : recommendedTypes) {
            List<Segment> candidates = segmentRepository
                    .findByFilters(tenantId, null, null, recType, null, null,
                            org.springframework.data.domain.PageRequest.of(0, 5))
                    .getContent();
            for (Segment seg : candidates) {
                if (!currentSegmentIds.contains(seg.getId())) {
                    SegmentRecommendationDTO rec = new SegmentRecommendationDTO();
                    rec.setSegmentId(seg.getId());
                    rec.setSegmentName(seg.getName());
                    rec.setSegmentType(seg.getSegmentType());
                    rec.setReason("基于已有段落类型 " + currentTypes + " 推荐");
                    rec.setRelevanceScore(0.8);
                    recommendations.add(rec);
                }
            }
        }

        return recommendations;
    }

    /**
     * Analyze duplicate segments within the current tenant.
     * Scans non-component segments and identifies pairs with text content similarity > 80%.
     */
    @Transactional(readOnly = true)
    public DuplicateAnalysisDTO analyzeDuplicates() {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Segment> segments = segmentRepository.findByTenantIdAndIsComponent(tenantId, false);

        // Extract text content from each segment's .docx
        Map<Long, String> segmentTexts = new HashMap<>();
        for (Segment seg : segments) {
            String text = extractTextFromDocx(seg.getFilePath());
            if (text != null && !text.isBlank()) {
                segmentTexts.put(seg.getId(), text);
            }
        }

        // Compare pairs for similarity
        List<DuplicateAnalysisDTO.DuplicatePair> duplicates = new ArrayList<>();
        List<Long> ids = new ArrayList<>(segmentTexts.keySet());
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                String textA = segmentTexts.get(ids.get(i));
                String textB = segmentTexts.get(ids.get(j));
                double similarity = calculateSimilarity(textA, textB);
                if (similarity > 80.0) {
                    DuplicateAnalysisDTO.DuplicatePair pair = new DuplicateAnalysisDTO.DuplicatePair();
                    Long idA = ids.get(i);
                    Long idB = ids.get(j);
                    pair.setSegmentIdA(idA);
                    pair.setSegmentIdB(idB);
                    Segment segA = segments.stream().filter(s -> s.getId().equals(idA)).findFirst().orElse(null);
                    Segment segB = segments.stream().filter(s -> s.getId().equals(idB)).findFirst().orElse(null);
                    pair.setSegmentNameA(segA != null ? segA.getName() : "");
                    pair.setSegmentNameB(segB != null ? segB.getName() : "");
                    pair.setSimilarityPercent(Math.round(similarity * 100.0) / 100.0);
                    duplicates.add(pair);
                }
            }
        }

        DuplicateAnalysisDTO result = new DuplicateAnalysisDTO();
        result.setDuplicates(duplicates);
        return result;
    }

    /**
     * Returns the list of preset segment templates plus custom templates for the current tenant.
     */
    @Transactional(readOnly = true)
    public List<SegmentTemplateDTO> listSegmentTemplates() {
        List<SegmentTemplateDTO> result = new ArrayList<>(PRESET_TEMPLATES);

        // Add custom templates: segments marked as templates (is_component = true with segment_type set)
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            List<Segment> customTemplates = segmentRepository.findByTenantIdAndIsComponent(tenantId, true);
            for (Segment seg : customTemplates) {
                SegmentTemplateDTO dto = new SegmentTemplateDTO();
                dto.setId(seg.getId());
                dto.setName(seg.getName());
                dto.setDescription(seg.getDescription());
                dto.setSegmentType(seg.getSegmentType());
                dto.setPreset(false);
                dto.setCreatedBy(seg.getCreatedBy());
                result.add(dto);
            }
        }

        return result;
    }

    /**
     * Save a segment as a custom template by promoting it to a component.
     */
    @Transactional
    public SegmentTemplateDTO saveAsTemplate(Long segmentId) {
        Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SEGMENT_NOT_FOUND, "段落不存在"));

        if (!segment.isComponent()) {
            segment.setComponent(true);
            segmentRepository.save(segment);
        }

        SegmentTemplateDTO dto = new SegmentTemplateDTO();
        dto.setId(segment.getId());
        dto.setName(segment.getName());
        dto.setDescription(segment.getDescription());
        dto.setSegmentType(segment.getSegmentType());
        dto.setPreset(false);
        dto.setCreatedBy(segment.getCreatedBy());
        return dto;
    }

    // ── Private helpers ──

    private AssemblyConfigDTO parseAssemblyConfig(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, AssemblyConfigDTO.class);
        } catch (Exception e) {
            log.warn("Failed to parse assembly_config: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract plain text from a .docx file stored in MinIO.
     * Reads word/document.xml from the ZIP and strips XML tags.
     */
    private String extractTextFromDocx(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName).object(filePath).build());
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("word/document.xml")) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    // Strip XML tags to get plain text
                    return xml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract text from docx {}: {}", filePath, e.getMessage());
        }
        return null;
    }

    /**
     * Calculate text similarity percentage using character-level bigram overlap (Dice coefficient).
     */
    double calculateSimilarity(String textA, String textB) {
        if (textA == null || textB == null) return 0.0;
        if (textA.equals(textB)) return 100.0;
        if (textA.length() < 2 || textB.length() < 2) return 0.0;

        Set<String> bigramsA = getBigrams(textA);
        Set<String> bigramsB = getBigrams(textB);

        int intersectionSize = 0;
        Set<String> smaller = bigramsA.size() <= bigramsB.size() ? bigramsA : bigramsB;
        Set<String> larger = bigramsA.size() > bigramsB.size() ? bigramsA : bigramsB;
        for (String bigram : smaller) {
            if (larger.contains(bigram)) {
                intersectionSize++;
            }
        }

        return (2.0 * intersectionSize) / (bigramsA.size() + bigramsB.size()) * 100.0;
    }

    private Set<String> getBigrams(String text) {
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            bigrams.add(text.substring(i, i + 2));
        }
        return bigrams;
    }
}
