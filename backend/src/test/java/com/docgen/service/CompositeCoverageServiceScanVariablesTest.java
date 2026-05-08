package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.entity.Template;
import com.docgen.repository.TemplateRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS-02-T07: verifies composite coverage delegates variable discovery to Docxtemplater {@code POST /scan-variables}.
 */
@ExtendWith(MockitoExtension.class)
class CompositeCoverageServiceScanVariablesTest {

    private static final String DOCXTEMPLATER_BASE = "http://localhost:3000";

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private AssemblyConfigService assemblyConfigService;
    @Mock
    private MinioClient minioClient;
    @Mock
    private RestTemplate restTemplate;

    @Test
    void scanVariablesFromFile_callsScanVariablesEndpoint() {
        when(restTemplate.exchange(
                eq(DOCXTEMPLATER_BASE + "/scan-variables"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(Map.of("variables", List.of("a", "b")), HttpStatus.OK));

        CompositeCoverageService svc = new CompositeCoverageService(
                templateRepository, assemblyConfigService, minioClient, restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "docxtemplaterServiceUrl", DOCXTEMPLATER_BASE);

        Template composite = new Template();
        composite.setId(1L);
        composite.setTemplateType("COMPOSITE");
        composite.setAssemblyConfig("{}");
        AssemblyConfigDTO cfg = new AssemblyConfigDTO();
        AssemblySegmentEntry seg = new AssemblySegmentEntry();
        seg.setEnabled(true);
        seg.setName("s1");
        seg.setFilePath("tenant/seg.docx");
        cfg.setSegments(List.of(seg));
        when(templateRepository.findById(1L)).thenReturn(Optional.of(composite));
        when(assemblyConfigService.deserialize(any())).thenReturn(cfg);

        svc.checkCoverage(1L);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        assertTrue(urlCaptor.getValue().endsWith("/scan-variables"),
                "Expected scan-variables URL, got: " + urlCaptor.getValue());
    }

    @Test
    void scanVariablesFromFile_mapsResponseVariables() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(Map.of("variables", List.of("x", "y")), HttpStatus.OK));

        CompositeCoverageService svc = new CompositeCoverageService(
                templateRepository, assemblyConfigService, minioClient, restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "docxtemplaterServiceUrl", DOCXTEMPLATER_BASE);

        Template composite = new Template();
        composite.setId(2L);
        composite.setTemplateType("COMPOSITE");
        composite.setAssemblyConfig("{}");
        AssemblyConfigDTO cfg = new AssemblyConfigDTO();
        AssemblySegmentEntry seg = new AssemblySegmentEntry();
        seg.setEnabled(true);
        seg.setName("seg");
        seg.setFilePath("path/to.docx");
        cfg.setSegments(List.of(seg));
        when(templateRepository.findById(2L)).thenReturn(Optional.of(composite));
        when(assemblyConfigService.deserialize(any())).thenReturn(cfg);

        var report = svc.checkCoverage(2L);
        assertEquals(1, report.getSegmentCoverages().size());
        assertEquals(2, report.getSegmentCoverages().get(0).getTotalVariables());
    }
}
