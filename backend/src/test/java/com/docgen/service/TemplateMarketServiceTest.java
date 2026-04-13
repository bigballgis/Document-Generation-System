package com.docgen.service;

import com.docgen.dto.MarketTemplateDTO;
import com.docgen.dto.TemplateDTO;
import com.docgen.entity.MarketTemplate;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateCategory;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.MarketTemplateRepository;
import com.docgen.repository.TemplateCategoryRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.util.TenantContext;
import io.minio.CopyObjectArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateMarketServiceTest {

    @Mock
    private MarketTemplateRepository marketTemplateRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateCategoryRepository categoryRepository;
    @Mock
    private MinioClient minioClient;

    private TemplateMarketService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TemplateMarketService(
                marketTemplateRepository, templateRepository, categoryRepository, minioClient);
        // Set the @Value field that Spring would normally inject
        java.lang.reflect.Field bucketField = TemplateMarketService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen");
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Search tests ──

    @Test
    void searchMarketTemplates_withKeyword_returnsResults() {
        MarketTemplate mt = createMarketTemplate(1L, 10L);
        Template template = createTemplate(10L, "合同模板", "标准合同");
        Page<MarketTemplate> page = new PageImpl<>(List.of(mt));
        Pageable pageable = PageRequest.of(0, 10);

        when(marketTemplateRepository.searchMarketTemplates(1L, "合同", pageable)).thenReturn(page);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        Page<MarketTemplateDTO> result = service.searchMarketTemplates("合同", null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("合同模板", result.getContent().get(0).getName());
    }

    @Test
    void searchMarketTemplates_withCategory_returnsResults() {
        MarketTemplate mt = createMarketTemplate(1L, 10L);
        Template template = createTemplate(10L, "报告模板", "月度报告");
        template.setCategoryId(5L);
        TemplateCategory category = new TemplateCategory();
        category.setId(5L);
        category.setName("报告");
        Page<MarketTemplate> page = new PageImpl<>(List.of(mt));
        Pageable pageable = PageRequest.of(0, 10);

        when(marketTemplateRepository.searchMarketTemplatesWithCategory(1L, null, 5L, pageable)).thenReturn(page);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));

        Page<MarketTemplateDTO> result = service.searchMarketTemplates(null, 5L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("报告", result.getContent().get(0).getCategory());
    }

    @Test
    void searchMarketTemplates_emptyResults() {
        Pageable pageable = PageRequest.of(0, 10);
        when(marketTemplateRepository.searchMarketTemplates(1L, "不存在", pageable))
                .thenReturn(Page.empty());

        Page<MarketTemplateDTO> result = service.searchMarketTemplates("不存在", null, pageable);

        assertEquals(0, result.getTotalElements());
    }

    // ── Copy tests ──

    @Test
    void copyFromMarket_success() throws Exception {
        MarketTemplate mt = createMarketTemplate(1L, 10L);
        Template sourceTemplate = createTemplate(10L, "发票模板", "标准发票");
        sourceTemplate.setTemplateFilePath("templates/2/invoice.docx");

        when(marketTemplateRepository.findById(1L)).thenReturn(Optional.of(mt));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(sourceTemplate));
        when(minioClient.copyObject(any(CopyObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId(100L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });
        when(marketTemplateRepository.save(any(MarketTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateDTO result = service.copyFromMarket(1L);

        assertNotNull(result);
        assertEquals("发票模板", result.getName());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(1L, result.getTenantId());
        // Verify usage count incremented
        verify(marketTemplateRepository).save(argThat(saved -> saved.getUsageCount() == 1));
    }

    @Test
    void copyFromMarket_marketTemplateNotFound_throws() {
        when(marketTemplateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.copyFromMarket(99L));
    }

    @Test
    void copyFromMarket_sourceTemplateNotFound_throws() {
        MarketTemplate mt = createMarketTemplate(1L, 999L);
        when(marketTemplateRepository.findById(1L)).thenReturn(Optional.of(mt));
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.copyFromMarket(1L));
    }

    // ── Share tests ──

    @Test
    void shareToMarket_success() {
        Template template = createTemplate(10L, "证书模板", "荣誉证书");
        template.setCreatedBy(5L);

        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(marketTemplateRepository.existsByTemplateId(10L)).thenReturn(false);
        when(marketTemplateRepository.save(any(MarketTemplate.class))).thenAnswer(inv -> {
            MarketTemplate mt = inv.getArgument(0);
            mt.setId(1L);
            mt.setCreatedAt(Instant.now());
            return mt;
        });
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        MarketTemplateDTO result = service.shareToMarket(10L, "GLOBAL");

        assertNotNull(result);
        assertEquals("GLOBAL", result.getShareScope());
        assertEquals(5L, result.getSharedBy());
    }

    @Test
    void shareToMarket_tenantInternal_success() {
        Template template = createTemplate(10L, "内部模板", "内部使用");
        template.setCreatedBy(5L);

        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(marketTemplateRepository.existsByTemplateId(10L)).thenReturn(false);
        when(marketTemplateRepository.save(any(MarketTemplate.class))).thenAnswer(inv -> {
            MarketTemplate mt = inv.getArgument(0);
            mt.setId(2L);
            mt.setCreatedAt(Instant.now());
            return mt;
        });

        MarketTemplateDTO result = service.shareToMarket(10L, "TENANT_INTERNAL");

        assertEquals("TENANT_INTERNAL", result.getShareScope());
    }

    @Test
    void shareToMarket_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.shareToMarket(99L, "GLOBAL"));
    }

    @Test
    void shareToMarket_alreadyShared_throws() {
        Template template = createTemplate(10L, "已分享模板", "desc");
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(marketTemplateRepository.existsByTemplateId(10L)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.shareToMarket(10L, "GLOBAL"));
    }

    // ── Helpers ──

    private MarketTemplate createMarketTemplate(Long id, Long templateId) {
        MarketTemplate mt = new MarketTemplate();
        mt.setId(id);
        mt.setTemplateId(templateId);
        mt.setSharedBy(5L);
        mt.setShareScope("GLOBAL");
        mt.setUsageCount(0);
        mt.setRating(BigDecimal.valueOf(4.5));
        mt.setCreatedAt(Instant.now());
        return mt;
    }

    private Template createTemplate(Long id, String name, String description) {
        Template t = new Template();
        t.setId(id);
        t.setTenantId(1L);
        t.setName(name);
        t.setDescription(description);
        t.setTemplateFilePath("templates/1/test.docx");
        t.setOutputFormat("WORD");
        t.setStorageStrategy("TEMP");
        t.setCreatedBy(5L);
        t.setStatus("ACTIVE");
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }
}
