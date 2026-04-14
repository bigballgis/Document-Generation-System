package com.docgen.integration;

import com.docgen.entity.Tenant;
import com.docgen.entity.User;
import com.docgen.repository.*;
import com.docgen.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CompositeTemplateController REST API endpoints.
 * Uses Testcontainers (PostgreSQL, Redis, MinIO) via BaseIntegrationTest.
 *
 * <p>Updated for inline segment model — no longer references segments table or
 * SegmentRepository. Tests use filePath/name/segmentType inline entries.</p>
 *
 * Validates: Requirements 2.1, 2.8, 2.9, 2.12
 */
class CompositeTemplateControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Long tenantId;
    private Long userId;
    private String token;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("CompCtrl-Tenant-" + System.nanoTime());
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername("compUser-" + System.nanoTime());
        user.setEmail("compUser-" + System.nanoTime() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test1234!"));
        user.setRole("TENANT_ADMIN");
        user = userRepository.save(user);
        userId = user.getId();

        token = jwtTokenProvider.generateAccessToken(userId, tenantId, "TENANT_ADMIN", null);
    }

    @AfterEach
    void tearDown() {
        templateRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    // ── Helper methods ──

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createCompositeTemplate(String name) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "description", "Test composite template"
        );
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), authHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates",
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    // ── 1. POST /api/composite-templates — Create composite template ──

    @Test
    void shouldCreateCompositeTemplate() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Annual Report Template",
                "description", "Full annual report"
        );
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), authHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates",
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Annual Report Template");
        assertThat(response.getBody().get("id")).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("DRAFT");
    }

    // ── 2. PUT /api/composite-templates/{id}/assembly-config — Update assembly config (inline mode) ──

    @Test
    void shouldUpdateAssemblyConfig() throws Exception {
        Map<String, Object> created = createCompositeTemplate("Config Test Template");
        Long templateId = ((Number) created.get("id")).longValue();

        Map<String, Object> configBody = Map.of(
                "segments", List.of(
                        Map.of("filePath", "segments/1/cover.docx", "name", "Cover Page",
                                "segmentType", "COVER", "position", 0, "enabled", true, "pageBreakBefore", false),
                        Map.of("filePath", "segments/2/content.docx", "name", "Content Body",
                                "segmentType", "CHAPTER", "position", 1, "enabled", true, "pageBreakBefore", true)
                )
        );
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(configBody), authHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/assembly-config",
                HttpMethod.PUT,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("segments")).isNotNull();
        List<?> segments = (List<?>) response.getBody().get("segments");
        assertThat(segments).hasSize(2);
    }

    // ── 3. PUT with all disabled segments — Should return 422 ──

    @Test
    void shouldReturn422WhenAllSegmentsDisabled() throws Exception {
        Map<String, Object> created = createCompositeTemplate("Empty Config Template");
        Long templateId = ((Number) created.get("id")).longValue();

        Map<String, Object> configBody = Map.of(
                "segments", List.of(
                        Map.of("filePath", "segments/1/a.docx", "name", "Disabled Seg 1",
                                "position", 0, "enabled", false),
                        Map.of("filePath", "segments/2/b.docx", "name", "Disabled Seg 2",
                                "position", 1, "enabled", false)
                )
        );
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(configBody), authHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/assembly-config",
                HttpMethod.PUT,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── 4. PUT with empty filePath — Should return 422 ──

    @Test
    void shouldReturn422WhenFilePathEmpty() throws Exception {
        Map<String, Object> created = createCompositeTemplate("Bad FilePath Template");
        Long templateId = ((Number) created.get("id")).longValue();

        Map<String, Object> configBody = Map.of(
                "segments", List.of(
                        Map.of("filePath", "", "name", "Missing File",
                                "position", 0, "enabled", true)
                )
        );
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(configBody), authHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/assembly-config",
                HttpMethod.PUT,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ── 5. GET /api/composite-templates/{id}/assembly-config — Get assembly config ──

    @Test
    void shouldGetAssemblyConfig() throws Exception {
        Map<String, Object> created = createCompositeTemplate("Get Config Template");
        Long templateId = ((Number) created.get("id")).longValue();

        // First set the config with inline segments
        Map<String, Object> configBody = Map.of(
                "segments", List.of(
                        Map.of("filePath", "segments/1/test.docx", "name", "Config Segment",
                                "segmentType", "CHAPTER", "position", 0, "enabled", true)
                )
        );
        HttpEntity<String> putRequest = new HttpEntity<>(objectMapper.writeValueAsString(configBody), authHeaders());
        restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/assembly-config",
                HttpMethod.PUT,
                putRequest,
                Map.class);

        // Then get it
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/assembly-config",
                HttpMethod.GET,
                new HttpEntity<>(getHeaders),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<?> segments = (List<?>) response.getBody().get("segments");
        assertThat(segments).hasSize(1);
    }

    @Test
    void shouldReturnEmptyConfigWhenNoneSet() throws Exception {
        Map<String, Object> created = createCompositeTemplate("No Config Template");
        Long templateId = ((Number) created.get("id")).longValue();

        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/assembly-config",
                HttpMethod.GET,
                new HttpEntity<>(getHeaders),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<?> segments = (List<?>) response.getBody().get("segments");
        assertThat(segments).isEmpty();
    }

    // ── 6. POST /api/composite-templates/{id}/preview — Preview ──

    @Test
    void shouldPreviewCompositeTemplate() throws Exception {
        Map<String, Object> created = createCompositeTemplate("Preview Template");
        Long templateId = ((Number) created.get("id")).longValue();

        // Set assembly config with inline segments
        Map<String, Object> configBody = Map.of(
                "segments", List.of(
                        Map.of("filePath", "segments/1/preview1.docx", "name", "Preview Seg 1",
                                "segmentType", "COVER", "position", 0, "enabled", true),
                        Map.of("filePath", "segments/2/preview2.docx", "name", "Preview Seg 2",
                                "segmentType", "CHAPTER", "position", 1, "enabled", false)
                )
        );
        HttpEntity<String> putRequest = new HttpEntity<>(objectMapper.writeValueAsString(configBody), authHeaders());
        restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/assembly-config",
                HttpMethod.PUT,
                putRequest,
                Map.class);

        // Preview
        HttpHeaders previewHeaders = new HttpHeaders();
        previewHeaders.setBearerAuth(token);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates/" + templateId + "/preview",
                HttpMethod.POST,
                new HttpEntity<>(previewHeaders),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<?> segmentPreviews = (List<?>) response.getBody().get("segmentPreviews");
        assertThat(segmentPreviews).hasSize(2);
    }

    // ── 7. Authentication required ──

    @Test
    void shouldReturnUnauthorizedWithoutToken() throws Exception {
        Map<String, Object> body = Map.of("name", "Unauth Template");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/composite-templates",
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
