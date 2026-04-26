package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallbackDocumentDownloadHelperTest {

    @Mock
    private RestTemplate restTemplate;

    private CallbackDocumentDownloadHelper helper;

    @BeforeEach
    void setUp() {
        helper = new CallbackDocumentDownloadHelper(restTemplate);
    }

    @Test
    void downloadOnlyOfficeDocx_returnsBytes_forValidOctetStream() {
        byte[] payload = "fake-docx".getBytes(StandardCharsets.UTF_8);
        stubExecute(payload, MediaType.APPLICATION_OCTET_STREAM);

        byte[] result = helper.downloadOnlyOfficeDocx("http://onlyoffice/download", 1024);

        assertArrayEquals(payload, result);
        verify(restTemplate).execute(eq("http://onlyoffice/download"), eq(HttpMethod.GET), isNull(), any());
    }

    @Test
    void downloadOnlyOfficeDocx_returnsNull_whenBodyMissing() {
        when(restTemplate.execute(eq("http://onlyoffice/download"), eq(HttpMethod.GET), isNull(), any(ResponseExtractor.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    ResponseExtractor<byte[]> extractor =
                            (ResponseExtractor<byte[]>) invocation.getArgument(3);
                    ClientHttpResponse response = mock(ClientHttpResponse.class);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    when(response.getHeaders()).thenReturn(headers);
                    when(response.getBody()).thenReturn(null);
                    return extractor.extractData(response);
                });

        assertNull(helper.downloadOnlyOfficeDocx("http://onlyoffice/download", 1024));
    }

    @Test
    void downloadOnlyOfficeDocx_rejectsOversizedPayload() {
        byte[] huge = new byte[5000];
        stubExecute(huge, MediaType.APPLICATION_OCTET_STREAM);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> helper.downloadOnlyOfficeDocx("http://onlyoffice/download", 100));

        assertEquals(ErrorCode.ONLYOFFICE_CALLBACK_FAILED, ex.getErrorCode());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getHttpStatus());
    }

    @Test
    void downloadOnlyOfficeDocx_rejectsUnexpectedContentType() {
        when(restTemplate.execute(eq("http://onlyoffice/download"), eq(HttpMethod.GET), isNull(), any(ResponseExtractor.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    ResponseExtractor<byte[]> extractor =
                            (ResponseExtractor<byte[]>) invocation.getArgument(3);
                    ClientHttpResponse response = mock(ClientHttpResponse.class);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.TEXT_PLAIN);
                    when(response.getHeaders()).thenReturn(headers);
                    return extractor.extractData(response);
                });

        BusinessException ex = assertThrows(BusinessException.class,
                () -> helper.downloadOnlyOfficeDocx("http://onlyoffice/download", 1024));

        assertEquals(ErrorCode.ONLYOFFICE_CALLBACK_FAILED, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    private void stubExecute(byte[] body, MediaType contentType) {
        when(restTemplate.execute(eq("http://onlyoffice/download"), eq(HttpMethod.GET), isNull(), any(ResponseExtractor.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    ResponseExtractor<byte[]> extractor =
                            (ResponseExtractor<byte[]>) invocation.getArgument(3);
                    ClientHttpResponse response = mock(ClientHttpResponse.class);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(contentType);
                    when(response.getHeaders()).thenReturn(headers);
                    when(response.getBody()).thenReturn(new ByteArrayInputStream(body));
                    return extractor.extractData(response);
                });
    }
}
