package com.docgen.integration;

import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.entity.Tenant;
import com.docgen.entity.User;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TenantRepository;
import com.docgen.repository.UserRepository;
import com.docgen.util.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for batch-delete and batch-update parameter APIs.
 * Validates: Requirements 9.1-9.4 (transaction atomicity, error handling)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParameterBatchApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private ParameterRepository parameterRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;
    private Long tenantId;
    private Long userId;
    private Long templateId;

    @BeforeEach
    void setUp() {
        // Create tenant
        Tenant tenant = new Tenant();
        tenant.setName("batch-test-tenant-" + System.nanoTime());
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        // Create user
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername("batchuser-" + System.nanoTime());
        user.setEmail("batch-" + System.nanoTime() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test1234!"));
        user.setRole("TENANT_ADMIN");
        user = userRepository.save(user);
        userId = user.getId();

        // Generate JWT token
        accessToken = jwtTokenProvider.generateAccessToken(userId, tenantId, "TENANT_ADMIN", null);

        // Create template
        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName("Batch Test Template");
        template.setDescription("Template for batch API tests");
        template.setTemplateFilePath("/test/batch_test.docx");
        template.setOutputFormat("WORD");
        template.setCreatedBy(userId);
        template.setStatus("DRAFT");
        template = templateRepository.save(template);
        templateId = template.getId();
    }

    @AfterEach
    void tearDown() {
        parameterRepository.deleteAll();
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

    private ParameterDefinition createParam(String name, Long parentId, String dataType, int sortOrder) {
        ParameterDefinition param = new ParameterDefinition();
        param.setTemplateId(templateId);
        param.setParentId(parentId);
        param.setName(name);
        param.setParameterType("REQUEST");
        param.setDataType(dataType);
        param.setRequired(true);
        param.setSortOrder(sortOrder);
        return parameterRepository.save(param);
    }

    // ── batch-delete tests ──

    @Test
    @Order(1)
    void batchDelete_shouldDeleteParametersAndReturnNoContent() {
        ParameterDefinition p1 = createParam("param_a", null, "STRING", 0);
        ParameterDefinition p2 = createParam("param_b", null, "NUMBER", 1);
        ParameterDefinition p3 = createParam("param_c", null, "STRING", 2);

        Map<String, Object> request = Map.of("ids", List.of(p1.getId(), p2.getId()));

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/batch-delete",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify only p3 remains
        List<ParameterDefinition> remaining = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getName()).isEqualTo("param_c");
    }

    @Test
    @Order(2)
    void batchDelete_shouldCascadeDeleteChildren() {
        ParameterDefinition parent = createParam("parent_obj", null, "OBJECT", 0);
        ParameterDefinition child1 = createParam("child_1", parent.getId(), "STRING", 0);
        ParameterDefinition child2 = createParam("child_2", parent.getId(), "NUMBER", 1);

        Map<String, Object> request = Map.of("ids", List.of(parent.getId()));

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/batch-delete",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // All three should be gone (parent + 2 children via CASCADE)
        List<ParameterDefinition> remaining = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        assertThat(remaining).isEmpty();
    }

    @Test
    @Order(3)
    void batchDelete_shouldReturn400ForInvalidIds() {
        ParameterDefinition p1 = createParam("valid_param", null, "STRING", 0);

        Map<String, Object> request = Map.of("ids", List.of(p1.getId(), 999999L));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/batch-delete",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("PARAMETER_BATCH_INVALID_IDS");

        // Original parameter should still exist (transaction rolled back)
        assertThat(parameterRepository.findById(p1.getId())).isPresent();
    }

    // ── batch-update tests ──

    @Test
    @Order(4)
    void batchUpdate_shouldUpdateParametersAndReturnUpdatedList() {
        ParameterDefinition p1 = createParam("old_name_a", null, "STRING", 0);
        ParameterDefinition p2 = createParam("old_name_b", null, "STRING", 1);

        List<Map<String, Object>> items = List.of(
                Map.of("id", p1.getId(), "version", p1.getVersion(), "name", "new_name_a", "description", "updated desc a"),
                Map.of("id", p2.getId(), "version", p2.getVersion(), "name", "new_name_b", "description", "updated desc b")
        );
        Map<String, Object> request = Map.of("items", items);

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/batch-update",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);

        // Verify in DB
        ParameterDefinition updated1 = parameterRepository.findById(p1.getId()).orElseThrow();
        ParameterDefinition updated2 = parameterRepository.findById(p2.getId()).orElseThrow();
        assertThat(updated1.getName()).isEqualTo("new_name_a");
        assertThat(updated1.getDescription()).isEqualTo("updated desc a");
        assertThat(updated2.getName()).isEqualTo("new_name_b");
        assertThat(updated2.getDescription()).isEqualTo("updated desc b");
    }

    @Test
    @Order(5)
    void batchUpdate_shouldReturn400WithAllValidationErrors() {
        ParameterDefinition p1 = createParam("param_x", null, "STRING", 0);
        ParameterDefinition p2 = createParam("param_y", null, "STRING", 1);

        // p1: invalid name (starts with digit), p2: duplicate name with p1's original name
        List<Map<String, Object>> items = List.of(
                Map.of("id", p1.getId(), "version", p1.getVersion(), "name", "123invalid"),
                Map.of("id", p2.getId(), "version", p2.getVersion(), "name", "param_x")
        );
        Map<String, Object> request = Map.of("items", items);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/batch-update",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("PARAMETER_BATCH_VALIDATION_FAILED");

        // Verify atomicity: neither parameter should be modified
        ParameterDefinition unchanged1 = parameterRepository.findById(p1.getId()).orElseThrow();
        ParameterDefinition unchanged2 = parameterRepository.findById(p2.getId()).orElseThrow();
        assertThat(unchanged1.getName()).isEqualTo("param_x");
        assertThat(unchanged2.getName()).isEqualTo("param_y");
    }

    @Test
    @Order(6)
    void batchUpdate_shouldReturn409ForOptimisticLockConflict() {
        ParameterDefinition p1 = createParam("lock_param", null, "STRING", 0);

        // Use a stale version number (current version + 10)
        int staleVersion = p1.getVersion() + 10;
        List<Map<String, Object>> items = List.of(
                Map.of("id", p1.getId(), "version", staleVersion, "name", "should_not_update")
        );
        Map<String, Object> request = Map.of("items", items);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/batch-update",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("PARAMETER_CONCURRENT_MODIFICATION");

        // Verify parameter unchanged
        ParameterDefinition unchanged = parameterRepository.findById(p1.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("lock_param");
    }
}
