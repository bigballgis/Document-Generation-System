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
 * Integration tests for JSON import parameter API.
 * Validates: Requirements 3.1-3.9 (end-to-end JSON import flow)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParameterJsonImportIntegrationTest extends BaseIntegrationTest {

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
        Tenant tenant = new Tenant();
        tenant.setName("json-import-tenant-" + System.nanoTime());
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername("jsonuser-" + System.nanoTime());
        user.setEmail("json-" + System.nanoTime() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test1234!"));
        user.setRole("TENANT_ADMIN");
        user = userRepository.save(user);
        userId = user.getId();

        accessToken = jwtTokenProvider.generateAccessToken(userId, tenantId, "TENANT_ADMIN", null);

        Template template = new Template();
        template.setTenantId(tenantId);
        template.setName("JSON Import Test Template");
        template.setDescription("Template for JSON import tests");
        template.setTemplateFilePath("/test/json_import_test.docx");
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

    @Test
    @Order(1)
    void jsonImport_flatObject_shouldCreateParametersInDb() {
        String jsonData = """
                {"company": "Acme", "revenue": 1000000, "active": true}
                """;
        Map<String, Object> request = Map.of("jsonData", jsonData);

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/json-import",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(3);

        // Verify parameters in DB
        List<ParameterDefinition> params = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        assertThat(params).hasSize(3);

        ParameterDefinition company = params.stream().filter(p -> p.getName().equals("company")).findFirst().orElseThrow();
        assertThat(company.getDataType()).isEqualTo("STRING");
        assertThat(company.getParentId()).isNull();

        ParameterDefinition revenue = params.stream().filter(p -> p.getName().equals("revenue")).findFirst().orElseThrow();
        assertThat(revenue.getDataType()).isEqualTo("NUMBER");

        ParameterDefinition active = params.stream().filter(p -> p.getName().equals("active")).findFirst().orElseThrow();
        assertThat(active.getDataType()).isEqualTo("BOOLEAN");
    }

    @Test
    @Order(2)
    void jsonImport_nestedObject_shouldCreateTreeStructure() {
        String jsonData = """
                {
                  "address": {
                    "street": "123 Main St",
                    "city": "Springfield",
                    "zip": 12345
                  }
                }
                """;
        Map<String, Object> request = Map.of("jsonData", jsonData);

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/json-import",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify tree structure in DB
        List<ParameterDefinition> params = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        assertThat(params).hasSize(4); // address + street + city + zip

        ParameterDefinition address = params.stream()
                .filter(p -> p.getName().equals("address") && p.getParentId() == null)
                .findFirst().orElseThrow();
        assertThat(address.getDataType()).isEqualTo("OBJECT");

        // Children should reference address as parent
        List<ParameterDefinition> children = params.stream()
                .filter(p -> address.getId().equals(p.getParentId()))
                .toList();
        assertThat(children).hasSize(3);
        assertThat(children.stream().map(ParameterDefinition::getName))
                .containsExactlyInAnyOrder("street", "city", "zip");

        ParameterDefinition zip = children.stream().filter(p -> p.getName().equals("zip")).findFirst().orElseThrow();
        assertThat(zip.getDataType()).isEqualTo("NUMBER");
    }

    @Test
    @Order(3)
    void jsonImport_invalidJson_shouldReturn400() {
        String invalidJson = "{ not valid json }";
        Map<String, Object> request = Map.of("jsonData", invalidJson);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/json-import",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("PARAMETER_JSON_IMPORT_FAILED");

        // No parameters should be created
        List<ParameterDefinition> params = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        assertThat(params).isEmpty();
    }

    @Test
    @Order(4)
    void jsonImport_emptyJsonObject_shouldReturn400() {
        Map<String, Object> request = Map.of("jsonData", "{}");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateId + "/parameters/json-import",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("PARAMETER_JSON_IMPORT_FAILED");

        List<ParameterDefinition> params = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        assertThat(params).isEmpty();
    }
}
