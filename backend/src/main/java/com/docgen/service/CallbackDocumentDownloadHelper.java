package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Bounded downloads for OnlyOffice callback payloads (.docx) using {@link RestTemplate} streaming.
 */
@Service
public class CallbackDocumentDownloadHelper {

    private final RestTemplate restTemplate;

    public CallbackDocumentDownloadHelper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Downloads a document from OnlyOffice with a maximum response body size and basic content-type checks.
     *
     * @return response bytes, or {@code null} if the response has no body stream
     */
    public byte[] downloadOnlyOfficeDocx(String url, long maxBytes) {
        return restTemplate.execute(url, HttpMethod.GET, null, response -> {
            enforceDocxContentType(response);
            return readAllBytesWithLimit(response.getBody(), maxBytes);
        });
    }

    private void enforceDocxContentType(ClientHttpResponse response) {
        try {
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null) {
                return;
            }
            String ct = contentType.toString().toLowerCase();
            if (ct.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || ct.contains(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
                return;
            }
            throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                    "Unexpected OnlyOffice download content type: " + contentType,
                    HttpStatus.BAD_REQUEST);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                    "Failed to validate OnlyOffice download content type",
                    HttpStatus.BAD_REQUEST, e);
        }
    }

    private byte[] readAllBytesWithLimit(InputStream in, long maxBytes) {
        if (in == null) {
            return null;
        }
        if (maxBytes <= 0) {
            throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                    "OnlyOffice download size limit must be positive",
                    HttpStatus.BAD_REQUEST);
        }

        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = input.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                            "OnlyOffice download exceeded max size limit",
                            HttpStatus.PAYLOAD_TOO_LARGE);
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ONLYOFFICE_CALLBACK_FAILED,
                    "Failed to download OnlyOffice edited document",
                    HttpStatus.BAD_REQUEST, e);
        }
    }
}
