package com.docgen.integration;

import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.entity.Tenant;
import com.docgen.entity.User;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVersionRepository;
import com.docgen.repository.TenantRepository;
import com.docgen.repository.UserRepository;
import com.docgen.util.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for template CRUD operations and version management.
 * Validates: Requirements 1, 10
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateCrudIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;
    private Long tenantId;
    private Long userId;

    @BeforeEach
    void setUp() {
        // Create tenant
        Tenant tenant = new Tenant();
        tenant.setName("test-tenant-" + System.nanoTime());
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        // Create user
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername("testuser-" + System.nanoTime());
        user.setEmail("test-" + System.nanoTime() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test1234!"));
        user.setRole("TENANT_ADMIN");
        user = userRepository.save(user);
        userId = user.getId();

        // Generate JWT token
        accessToken = jwtTokenProvider.generateAccessToken(userId, tenantId, "TENANT_ADMIN", null);
    }

    @AfterEach
    void tearDown() {
        templateVersionRepository.deleteAll();
        templateRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @Order(1)
    void shouldCreateTemplate() {
        Map<String, Object> request = Map.of(
                "name", "Invoice Template",
                "description", "Monthly invoice template",
                "outputFormat", "WORD"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Invoice Template");
        assertThat(response.getBody().get("status")).isEqualTo("DRAFT");
    }

    @Test
    @Order(2)
    void shouldGetTemplateById() {
        // Create template directly in DB
        Template template = createTestTemplate("Get Test Template");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + template.getId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Get Test Template");
    }

    @Test
    @Order(3)
    void shouldUpdateTemplate() {
        Template template = createTestTemplate("Original Name");

        Map<String, Object> updateRequest = Map.of(
                "name", "Updated Name",
                "description", "Updated description"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + template.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Updated Name");
    }

    @Test
    @Order(4)
    void shouldDeleteTemplate() {
        Template template = createTestTemplate("Delete Me");

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + template.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        Optional<Template> deleted = templateRepository.findById(template.getId());
        assertThat(deleted).isEmpty();
    }

    @Test
    @Order(5)
    void shouldListTemplatesWithPagination() {
        createTestTemplate("Template A");
        createTestTemplate("Template B");
        createTestTemplate("Template C");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates?page=0&size=2",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(6)
    void shouldCreateVersionOnUpdate() {
        Template template = createTestTemplate("Versioned Template");

        // Update the template to trigger version creation
        Map<String, Object> updateRequest = Map.of(
                "name", "Versioned Template v2",
                "description", "Updated for version test"
        );

        restTemplate.exchange(
                baseUrl() + "/api/templates/" + template.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, authHeaders()),
                Map.class);

        // Check versions
        List<TemplateVersion> versions = templateVersionRepository
                .findByTemplateIdOrderByVersionNumberDesc(template.getId());

        assertThat(versions).isNotEmpty();
    }

    @Test
    @Order(7)
    void shouldReturnUnauthorizedWithoutToken() {
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
    @Order(8)
    void putRenderConfig_singleTemplate_returnsBadRequest() {
        Template template = createTestTemplate("Single render-config");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + template.getId() + "/render-config",
                HttpMethod.PUT,
                new HttpEntity<>(validTextWatermarkRenderConfigBody(), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> err = (Map<String, Object>) response.getBody().get("error");
        assertThat(err).isNotNull();
        assertThat(String.valueOf(err.get("message"))).containsIgnoringCase("composite");
    }

    @Test
    @Order(9)
    void putRenderConfig_compositeTemplate_returnsOk() {
        Template template = createTestTemplate("Composite render-config", "COMPOSITE");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + template.getId() + "/render-config",
                HttpMethod.PUT,
                new HttpEntity<>(validTextWatermarkRenderConfigBody(), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        Object rc = response.getBody().get("renderConfig");
        assertThat(rc).isNotNull();
        assertThat(String.valueOf(rc)).contains("CONFIDENTIAL");
    }

    @Test
    @Order(10)
    void deleteRenderConfig_singleTemplate_withStoredConfig_returnsOk() {
        Template template = createTestTemplate("Single delete render-config");
        template.setRenderConfig(
                "{\"schemaVersion\":1,\"textWatermark\":{\"text\":\"X\",\"fontSize\":36,\"color\":\"#cccccc\",\"opacity\":0.3,\"rotation\":-45}}");
        template = templateRepository.save(template);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + template.getId() + "/render-config",
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Template reloaded = templateRepository.findById(template.getId()).orElseThrow();
        assertThat(reloaded.getRenderConfig()).isNull();
    }

    private static Map<String, Object> validTextWatermarkRenderConfigBody() {
        Map<String, Object> tw = new LinkedHashMap<>();
        tw.put("text", "CONFIDENTIAL");
        tw.put("fontSize", 36);
        tw.put("color", "#cccccc");
        tw.put("opacity", 0.3);
        tw.put("rotation", -45.0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1);
        body.put("textWatermark", tw);
        return body;
    }

    private Template createTestTemplate(String name) {
        return createTestTemplate(name, "SINGLE");
    }

    private Template createTestTemplate(String name, String templateType) {
        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName(name);
        template.setDescription("Test description");
        template.setTemplateFilePath("/test/path/" + name.replaceAll("\\s+", "_") + ".docx");
        template.setOutputFormat("WORD");
        template.setCreatedBy(userId);
        template.setStatus("DRAFT");
        template.setTemplateType(templateType);
        return templateRepository.save(template);
    }
}
