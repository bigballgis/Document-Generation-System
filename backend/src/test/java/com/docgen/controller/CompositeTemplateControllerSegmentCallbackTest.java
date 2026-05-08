package com.docgen.controller;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.service.CallbackDocumentDownloadHelper;
import com.docgen.service.CompositeCoverageService;
import com.docgen.service.CompositeImportExportService;
import com.docgen.service.CompositeTemplateService;
import com.docgen.service.ContentIsolationValidator;
import com.docgen.service.OnlyOfficeService;
import com.docgen.service.SegmentVersionService;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeTemplateControllerSegmentCallbackTest {

    private static final long TEMPLATE_ID = 42L;
    private static final int SEGMENT_INDEX = 0;

    @Mock
    private CompositeTemplateService compositeTemplateService;
    @Mock
    private CompositeCoverageService compositeCoverageService;
    @Mock
    private CompositeImportExportService compositeImportExportService;
    @Mock
    private ContentIsolationValidator contentIsolationValidator;
    @Mock
    private SegmentVersionService segmentVersionService;
    @Mock
    private OnlyOfficeService onlyOfficeService;
    @Mock
    private MinioClient minioClient;
    @Mock
    private CallbackDocumentDownloadHelper callbackDocumentDownloadHelper;

    private CompositeTemplateController controller;

    @BeforeEach
    void setUp() {
        when(onlyOfficeService.resolveCallbackDownloadFetchUrl(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        controller = new CompositeTemplateController(
                compositeTemplateService,
                compositeCoverageService,
                compositeImportExportService,
                contentIsolationValidator,
                segmentVersionService,
                onlyOfficeService,
                minioClient,
                callbackDocumentDownloadHelper);
        ReflectionTestUtils.setField(controller, "bucketName", "docgen");
        ReflectionTestUtils.setField(controller, "onlyOfficeCallbackMaxDownloadBytes", 1024L);
    }

    @Test
    void segmentCallback_whenDownloadUrlNotAllowed_returnsError1() {
        when(onlyOfficeService.isAllowedCallbackDownloadUrl("http://evil/download")).thenReturn(false);

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                SEGMENT_INDEX,
                "body",
                null,
                Map.of("status", 2, "url", "http://evil/download"));

        assertEquals(1, res.getBody().get("error"));
        verify(compositeTemplateService, never()).getAssemblyConfig(any());
    }

    @Test
    void segmentCallback_whenJwtInvalid_returnsError1() {
        when(onlyOfficeService.isAllowedCallbackDownloadUrl(anyString())).thenReturn(true);
        when(onlyOfficeService.isCallbackJwtRequired()).thenReturn(true);
        when(onlyOfficeService.isValidOnlyOfficeJwt("bad")).thenReturn(false);

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                SEGMENT_INDEX,
                "body",
                null,
                Map.of("status", 2, "url", "http://onlyoffice/download", "token", "bad"));

        assertEquals(1, res.getBody().get("error"));
        verify(compositeTemplateService, never()).getAssemblyConfig(any());
    }

    @Test
    void segmentCallback_whenAssemblyLookupFails_returnsError1() {
        when(onlyOfficeService.isAllowedCallbackDownloadUrl(anyString())).thenReturn(true);
        when(onlyOfficeService.isCallbackJwtRequired()).thenReturn(false);
        when(compositeTemplateService.getAssemblyConfig(TEMPLATE_ID))
                .thenThrow(new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND, "missing", HttpStatus.NOT_FOUND));

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                SEGMENT_INDEX,
                "body",
                null,
                Map.of("status", 2, "url", "http://onlyoffice/download"));

        assertEquals(1, res.getBody().get("error"));
        verify(callbackDocumentDownloadHelper, never()).downloadOnlyOfficeDocx(anyString(), anyLong());
    }

    @Test
    void segmentCallback_whenSegmentIndexInvalid_returnsError1() {
        when(onlyOfficeService.isAllowedCallbackDownloadUrl(anyString())).thenReturn(true);
        when(onlyOfficeService.isCallbackJwtRequired()).thenReturn(false);
        AssemblyConfigDTO dto = new AssemblyConfigDTO();
        dto.setSegments(List.of(new AssemblySegmentEntry()));
        when(compositeTemplateService.getAssemblyConfig(TEMPLATE_ID)).thenReturn(dto);

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                5,
                "body",
                null,
                Map.of("status", 2, "url", "http://onlyoffice/download"));

        assertEquals(1, res.getBody().get("error"));
        verify(callbackDocumentDownloadHelper, never()).downloadOnlyOfficeDocx(anyString(), anyLong());
    }

    @Test
    void segmentCallback_whenFilePathBlank_returnsError1() {
        when(onlyOfficeService.isAllowedCallbackDownloadUrl(anyString())).thenReturn(true);
        when(onlyOfficeService.isCallbackJwtRequired()).thenReturn(false);
        AssemblySegmentEntry seg = new AssemblySegmentEntry();
        seg.setFilePath("  ");
        AssemblyConfigDTO dto = new AssemblyConfigDTO();
        dto.setSegments(List.of(seg));
        when(compositeTemplateService.getAssemblyConfig(TEMPLATE_ID)).thenReturn(dto);

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                SEGMENT_INDEX,
                "body",
                null,
                Map.of("status", 2, "url", "http://onlyoffice/download"));

        assertEquals(1, res.getBody().get("error"));
        verify(callbackDocumentDownloadHelper, never()).downloadOnlyOfficeDocx(anyString(), anyLong());
    }

    @Test
    void segmentCallback_whenDownloadEmpty_returnsError1() {
        stubHappyPathAssembly();
        when(callbackDocumentDownloadHelper.downloadOnlyOfficeDocx(anyString(), anyLong()))
                .thenReturn(new byte[0]);

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                SEGMENT_INDEX,
                "body",
                null,
                Map.of("status", 2, "url", "http://onlyoffice/download"));

        assertEquals(1, res.getBody().get("error"));
        verify(contentIsolationValidator, never()).validate(any(), anyString());
    }

    @Test
    void segmentCallback_whenContentIsolationFails_returnsError1() throws Exception {
        stubHappyPathAssembly();
        when(callbackDocumentDownloadHelper.downloadOnlyOfficeDocx(anyString(), anyLong()))
                .thenReturn(new byte[]{1, 2, 3});
        doThrow(new BusinessException(ErrorCode.VALIDATION_FAILED, "isolation", HttpStatus.BAD_REQUEST))
                .when(contentIsolationValidator).validate(any(), eq("body"));

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                SEGMENT_INDEX,
                "body",
                null,
                Map.of("status", 2, "url", "http://onlyoffice/download"));

        assertEquals(1, res.getBody().get("error"));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void segmentCallback_whenSaveSucceeds_returnsError0() throws Exception {
        stubHappyPathAssembly();
        when(callbackDocumentDownloadHelper.downloadOnlyOfficeDocx(anyString(), anyLong()))
                .thenReturn(new byte[]{1, 2, 3});
        doNothing().when(contentIsolationValidator).validate(any(), eq("body"));
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));

        ResponseEntity<Map<String, Integer>> res = controller.handleSegmentCallback(
                TEMPLATE_ID,
                SEGMENT_INDEX,
                "body",
                null,
                Map.of("status", 2, "url", "http://onlyoffice/download"));

        assertEquals(0, res.getBody().get("error"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    private void stubHappyPathAssembly() {
        when(onlyOfficeService.isAllowedCallbackDownloadUrl(anyString())).thenReturn(true);
        when(onlyOfficeService.isCallbackJwtRequired()).thenReturn(false);
        AssemblySegmentEntry seg = new AssemblySegmentEntry();
        seg.setFilePath("composite/42/seg0.docx");
        AssemblyConfigDTO dto = new AssemblyConfigDTO();
        dto.setSegments(List.of(seg));
        when(compositeTemplateService.getAssemblyConfig(TEMPLATE_ID)).thenReturn(dto);
    }
}
