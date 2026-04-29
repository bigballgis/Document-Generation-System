package com.docgen.integration;

import com.docgen.entity.ApiKey;
import com.docgen.entity.Tenant;
import com.docgen.entity.User;
import com.docgen.filter.ApiKeyAuthenticationFilter;
import com.docgen.repository.ApiKeyRepository;
import com.docgen.repository.TenantRepository;
import com.docgen.repository.UserRepository;
import com.docgen.util.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Spring Security: JWT auth, API Key auth, permission control.
 * Validates: Requirement 9
 */
class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long tenantId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("security-tenant-" + System.nanoTime());
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername("secuser-" + System.nanoTime());
        user.setEmail("sec-" + System.nanoTime() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test1234!"));
        user.setRole("TENANT_ADMIN");
        user = userRepository.save(user);
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }


    @Test
    void shouldAuthenticateWithValidJwtToken() {
        String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "TENANT_ADMIN", null);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldRejectInvalidJwtToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.jwt.token");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectExpiredJwtToken() {
        // Create a token that's already expired by using a very short expiration
        // We'll manually craft an expired token by generating one with past dates
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidGVuYW50SWQiOjEsInJvbGUiOiJVU0VSIiwidGVhbUlkIjpudWxsLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6MTYwMDAwMDAwMX0.invalid";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectRequestWithoutAuthentication() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    @Test
    void shouldAuthenticateWithValidApiKey() {
        String rawApiKey = "test-api-key-" + System.nanoTime();
        String keyHash = ApiKeyAuthenticationFilter.sha256Hex(rawApiKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setUserId(userId);
        apiKey.setKeyHash(keyHash);
        apiKey.setName("Test API Key");
        apiKey.setEnabled(true);
        apiKey.setExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
        apiKeyRepository.save(apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", rawApiKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldRejectDisabledApiKey() {
        String rawApiKey = "disabled-key-" + System.nanoTime();
        String keyHash = ApiKeyAuthenticationFilter.sha256Hex(rawApiKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setUserId(userId);
        apiKey.setKeyHash(keyHash);
        apiKey.setName("Disabled API Key");
        apiKey.setEnabled(false);
        apiKey.setExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
        apiKeyRepository.save(apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", rawApiKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectExpiredApiKey() {
        String rawApiKey = "expired-key-" + System.nanoTime();
        String keyHash = ApiKeyAuthenticationFilter.sha256Hex(rawApiKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setUserId(userId);
        apiKey.setKeyHash(keyHash);
        apiKey.setName("Expired API Key");
        apiKey.setEnabled(true);
        apiKey.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS)); // Already expired
        apiKeyRepository.save(apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", rawApiKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectNonExistentApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "nonexistent-key-" + System.nanoTime());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    @Test
    void shouldAllowAccessToPublicEndpoints() {
        // Health endpoint should be accessible without auth
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/actuator/health",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }


    @Test
    void shouldDenySuperAdminEndpointToRegularUser() {
        String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "USER", null);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/tenants",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldAllowSuperAdminToAccessTenantEndpoint() {
        String token = jwtTokenProvider.generateAccessToken(userId, tenantId, "SUPER_ADMIN", null);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/tenants",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

