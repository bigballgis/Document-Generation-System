package com.docgen.integration;

import com.docgen.entity.Template;
import com.docgen.entity.Tenant;
import com.docgen.entity.User;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TenantRepository;
import com.docgen.repository.UserRepository;
import com.docgen.util.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for multi-tenant data isolation.
 * Verifies that tenant A cannot access tenant B's data.
 * Validates: Requirement 19
 */
class MultiTenantIsolationIntegrationTest extends BaseIntegrationTest {

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

    private Long tenantAId;
    private Long tenantBId;
    private String tokenTenantA;
    private String tokenTenantB;
    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() {
        // Create Tenant A
        Tenant tenantA = new Tenant();
        tenantA.setName("Tenant-A-" + System.nanoTime());
        tenantA.setStatus("ACTIVE");
        tenantA = tenantRepository.save(tenantA);
        tenantAId = tenantA.getId();

        // Create Tenant B
        Tenant tenantB = new Tenant();
        tenantB.setName("Tenant-B-" + System.nanoTime());
        tenantB.setStatus("ACTIVE");
        tenantB = tenantRepository.save(tenantB);
        tenantBId = tenantB.getId();

        // Create User A in Tenant A
        User userA = new User();
        userA.setTenantId(tenantAId);
        userA.setUsername("userA-" + System.nanoTime());
        userA.setEmail("userA-" + System.nanoTime() + "@example.com");
        userA.setPasswordHash(passwordEncoder.encode("Test1234!"));
        userA.setRole("TENANT_ADMIN");
        userA = userRepository.save(userA);
        userAId = userA.getId();

        // Create User B in Tenant B
        User userB = new User();
        userB.setTenantId(tenantBId);
        userB.setUsername("userB-" + System.nanoTime());
        userB.setEmail("userB-" + System.nanoTime() + "@example.com");
        userB.setPasswordHash(passwordEncoder.encode("Test1234!"));
        userB.setRole("TENANT_ADMIN");
        userB = userRepository.save(userB);
        userBId = userB.getId();

        tokenTenantA = jwtTokenProvider.generateAccessToken(userAId, tenantAId, "TENANT_ADMIN", null);
        tokenTenantB = jwtTokenProvider.generateAccessToken(userBId, tenantBId, "TENANT_ADMIN", null);
    }

    @AfterEach
    void tearDown() {
        templateRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void tenantAShouldNotSeeTenantBTemplates() {
        // Create template for Tenant A
        Template templateA = new Template();
        templateA.setTenantId(tenantAId);
        templateA.setName("Tenant A Template");
        templateA.setDescription("Belongs to Tenant A");
        templateA.setTemplateFilePath("/tenantA/template.docx");
        templateA.setCreatedBy(userAId);
        templateRepository.save(templateA);

        // Create template for Tenant B
        Template templateB = new Template();
        templateB.setTenantId(tenantBId);
        templateB.setName("Tenant B Template");
        templateB.setDescription("Belongs to Tenant B");
        templateB.setTemplateFilePath("/tenantB/template.docx");
        templateB.setCreatedBy(userBId);
        templateRepository.save(templateB);

        // Tenant A lists templates — should only see their own
        HttpHeaders headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenTenantA);

        ResponseEntity<Map> responseA = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headersA),
                Map.class);

        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The response should not contain Tenant B's template
        String body = responseA.getBody().toString();
        assertThat(body).contains("Tenant A Template");
        assertThat(body).doesNotContain("Tenant B Template");
    }

    @Test
    void tenantBShouldNotAccessTenantATemplateById() {
        // Create template for Tenant A
        Template templateA = new Template();
        templateA.setTenantId(tenantAId);
        templateA.setName("Secret Template A");
        templateA.setDescription("Tenant A only");
        templateA.setTemplateFilePath("/tenantA/secret.docx");
        templateA.setCreatedBy(userAId);
        templateA = templateRepository.save(templateA);

        // Tenant B tries to access Tenant A's template by ID
        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(tokenTenantB);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates/" + templateA.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                Map.class);

        // Should be 404 (not found due to tenant filter) or 403
        assertThat(response.getStatusCode().value()).isIn(403, 404);
    }

    @Test
    void disabledTenantUserShouldBeRejected() {
        // Disable Tenant A
        Tenant tenantA = tenantRepository.findById(tenantAId).orElseThrow();
        tenantA.setStatus("DISABLED");
        tenantRepository.save(tenantA);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenTenantA);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/templates",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        // Disabled tenant should get 403 or similar rejection
        assertThat(response.getStatusCode().value()).isIn(403, 401);
    }
}
