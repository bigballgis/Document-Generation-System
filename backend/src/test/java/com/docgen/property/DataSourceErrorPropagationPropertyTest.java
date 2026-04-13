package com.docgen.property;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.service.EncryptionService;
import com.docgen.service.HttpApiDataSourceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.mockito.MockedConstruction;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for data source error propagation completeness.
 *
 * <p>Verifies that when data source calls fail (timeout, HTTP 4xx/5xx, connection refused),
 * the service throws a standardized {@link BusinessException} with an appropriate error code
 * and a message containing useful failure information.</p>
 *
 * <p><b>Validates: Requirements 2.5, 3.5, 4.4</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 5: 数据源错误传播完整性")
class DataSourceErrorPropagationPropertyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Build a minimal config JSON for an HTTP API data source with the given retry settings.
     */
    private String buildConfigJson(int retryCount, int retryInterval, boolean exponentialBackoff) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "url", "http://test-api.example.com/data",
                    "method", "GET",
                    "authType", "NONE",
                    "timeout", 100,
                    "retryCount", retryCount,
                    "retryInterval", retryInterval,
                    "retryExponentialBackoff", exponentialBackoff
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpApiDataSourceService createService() {
        EncryptionService encryptionService = mock(EncryptionService.class);
        return new HttpApiDataSourceService(encryptionService);
    }

    // ── Property Tests ──

    /**
     * Property 5: For any timeout or connection-refused scenario, fetchData
     * should throw a BusinessException with DATASOURCE_TIMEOUT error code.
     */
    @Property(tries = 50)
    void timeoutErrorsShouldPropagateDatasourceTimeoutCode(
            @ForAll("timeoutExceptions") ResourceAccessException exception
    ) {
        HttpApiDataSourceService service = createService();
        String configJson = buildConfigJson(0, 100, false);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> when(mock.exchange(any(java.net.URI.class), any(HttpMethod.class),
                        any(HttpEntity.class), eq(String.class)))
                        .thenThrow(exception))) {

            BusinessException thrown = assertThrows(BusinessException.class, () ->
                    service.fetchData(configJson, Collections.emptyMap()));

            assertEquals(ErrorCode.DATASOURCE_TIMEOUT, thrown.getErrorCode(),
                    "Timeout/connection errors must use DATASOURCE_TIMEOUT error code");
            assertNotNull(thrown.getMessage(), "Error message must not be null");
            assertFalse(thrown.getMessage().isBlank(), "Error message must not be blank");
        }
    }

    /**
     * Property 5: For any HTTP 4xx/5xx error response, fetchData
     * should throw a BusinessException with DATASOURCE_CONNECTION_FAILED error code.
     */
    @Property(tries = 50)
    void httpErrorsShouldPropagateDatasourceConnectionFailedCode(
            @ForAll("httpErrorExceptions") RestClientResponseException exception
    ) {
        HttpApiDataSourceService service = createService();
        String configJson = buildConfigJson(0, 100, false);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> when(mock.exchange(any(java.net.URI.class), any(HttpMethod.class),
                        any(HttpEntity.class), eq(String.class)))
                        .thenThrow(exception))) {

            BusinessException thrown = assertThrows(BusinessException.class, () ->
                    service.fetchData(configJson, Collections.emptyMap()));

            assertEquals(ErrorCode.DATASOURCE_CONNECTION_FAILED, thrown.getErrorCode(),
                    "HTTP error responses must use DATASOURCE_CONNECTION_FAILED error code");
            assertNotNull(thrown.getMessage(), "Error message must not be null");
            assertFalse(thrown.getMessage().isBlank(), "Error message must not be blank");
        }
    }

    /**
     * Property 5: For any error scenario with retries configured, all retries
     * should be exhausted before throwing, and the final exception should still
     * carry the correct error code.
     */
    @Property(tries = 30)
    void retriesShouldBeExhaustedBeforeThrowingError(
            @ForAll("retryConfigs") RetryConfig retryConfig,
            @ForAll("allErrorExceptions") Exception exception
    ) {
        HttpApiDataSourceService service = createService();
        String configJson = buildConfigJson(retryConfig.retryCount(), retryConfig.retryInterval(),
                retryConfig.exponentialBackoff());

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> when(mock.exchange(any(java.net.URI.class), any(HttpMethod.class),
                        any(HttpEntity.class), eq(String.class)))
                        .thenThrow(exception))) {

            BusinessException thrown = assertThrows(BusinessException.class, () ->
                    service.fetchData(configJson, Collections.emptyMap()));

            // Verify the correct number of attempts were made
            RestTemplate constructedMock = mocked.constructed().get(0);
            int expectedAttempts = retryConfig.retryCount() + 1;
            verify(constructedMock, times(expectedAttempts))
                    .exchange(any(java.net.URI.class), any(HttpMethod.class),
                            any(HttpEntity.class), eq(String.class));

            // Error code should match the exception type
            if (exception instanceof ResourceAccessException) {
                assertEquals(ErrorCode.DATASOURCE_TIMEOUT, thrown.getErrorCode());
            } else {
                assertEquals(ErrorCode.DATASOURCE_CONNECTION_FAILED, thrown.getErrorCode());
            }
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<ResourceAccessException> timeoutExceptions() {
        return Arbitraries.oneOf(
                // Socket timeout
                Arbitraries.of(
                        new ResourceAccessException("I/O error on GET",
                                new SocketTimeoutException("Read timed out")),
                        new ResourceAccessException("I/O error on POST",
                                new SocketTimeoutException("Connect timed out"))
                ),
                // Connection refused
                Arbitraries.of(
                        new ResourceAccessException("I/O error on GET",
                                new ConnectException("Connection refused")),
                        new ResourceAccessException("I/O error on POST",
                                new ConnectException("Connection refused (Connection refused)"))
                ),
                // Generic I/O errors
                Arbitraries.strings().ascii().ofMinLength(5).ofMaxLength(50)
                        .map(msg -> new ResourceAccessException("I/O error: " + msg,
                                new java.io.IOException(msg)))
        );
    }

    @Provide
    Arbitrary<RestClientResponseException> httpErrorExceptions() {
        Arbitrary<Integer> clientErrors = Arbitraries.integers().between(400, 499);
        Arbitrary<Integer> serverErrors = Arbitraries.integers().between(500, 599);

        return Arbitraries.oneOf(clientErrors, serverErrors)
                .map(statusCode -> new RestClientResponseException(
                        "HTTP " + statusCode + " error",
                        HttpStatusCode.valueOf(statusCode),
                        "Error",
                        null,
                        "error body".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));
    }

    @Provide
    Arbitrary<Exception> allErrorExceptions() {
        return Arbitraries.oneOf(
                timeoutExceptions().map(e -> (Exception) e),
                httpErrorExceptions().map(e -> (Exception) e)
        );
    }

    @Provide
    Arbitrary<RetryConfig> retryConfigs() {
        return Arbitraries.integers().between(0, 2)
                .flatMap(retryCount ->
                        Arbitraries.integers().between(10, 50)
                                .flatMap(retryInterval ->
                                        Arbitraries.of(true, false)
                                                .map(exponential ->
                                                        new RetryConfig(retryCount, retryInterval, exponential))));
    }

    record RetryConfig(int retryCount, int retryInterval, boolean exponentialBackoff) {}
}
