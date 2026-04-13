package com.docgen.integration;

import com.docgen.entity.Segment;
import com.docgen.entity.Tenant;
import com.docgen.entity.User;
import com.docgen.repository.*;
import com.docgen.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SegmentController REST API endpoints.
 * Uses Testcontainers (PostgreSQL, Redis, MinIO) via BaseIntegrationTest.
 * Validates: Requirements 1.1-1.11
 */
class SegmentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SegmentRepository segmentRepository;

    @Autowired
    private SegmentVersionRepository segmentVersionRepository;

    @Autowired
    private SegmentTagMappingRepository segmentTagMappingRepository;

    @Autowired
    private SegmentFavoriteRepository segmentFavoriteRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Long tenantAId;
    private Long tenantBId;
    private Long userAId;
    private Long userBId;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        // Tenant A
        Tenant tenantA = new Tenant();
        tenantA.setName("SegCtrl-TenantA-" + System.nanoTime());
        tenantA.setStatus("ACTIVE");
        tenantA = tenantRepository.save(tenantA);
        tenantAId = tenantA.getId();

        // Tenant B
        Tenant tenantB = new Tenant();
        tenantB.setName("SegCtrl-TenantB-" + System.nanoTime());
        tenantB.setStatus("ACTIVE");
        tenantB = tenantRepository.save(tenantB);
        tenantBId = tenantB.getId();

        // User A in Tenant A
        User userA = new User();
        userA.setTenantId(tenantAId);
        userA.setUsername("segUserA-" + System.nanoTime());
        userA.setEmail("segUserA-" + System.nanoTime() + "@example.com");
        userA.setPasswordHash(passwordEncoder.encode("Test1234!"));
        userA.setRole("TENANT_ADMIN");
        userA = userRepository.save(userA);
        userAId = userA.getId();

        // User B in Tenant B
        User userB = new User();
        userB.setTenantId(tenantBId);
        userB.setUsername("segUserB-" + System.nanoTime());
        userB.setEmail("segUserB-" + System.nanoTime() + "@example.com");
        userB.setPasswordHash(passwordEncoder.encode("Test1234!"));
        userB.setRole("TENANT_ADMIN");
        userB = userRepository.save(userB);
        userBId = userB.getId();

        tokenA = jwtTokenProvider.generateAccessToken(userAId, tenantAId, "TENANT_ADMIN", null);
        tokenB = jwtTokenProvider.generateAccessToken(userBId, tenantBId, "TENANT_ADMIN", null);
    }

    @AfterEach
    void tearDown() {
        segmentFavoriteRepository.deleteAll();
        segmentTagMappingRepository.deleteAll();
        segmentVersionRepository.deleteAll();
        segmentRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    // ── Helper methods ──

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<MultiValueMap<String, Object>> buildCreateRequest(
            String token, String name, String description) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "name", name,
                "description", description
        );
        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // JSON part
        HttpHeaders jsonPartHeaders = new HttpHeaders();
        jsonPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("request", new HttpEntity<>(requestJson, jsonPartHeaders));

        // File part — minimal .docx-like content for testing
        ByteArrayResource fileResource = new ByteArrayResource("fake-docx-content".getBytes()) {
            @Override
            public String getFilename() {
                return "test-segment.docx";
            }
        };
        HttpHeaders filePartHeaders = new HttpHeaders();
        filePartHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        body.add("file", new HttpEntity<>(fileResource, filePartHeaders));

        return new HttpEntity<>(body, headers);
    }

    private Segment createSegmentInDb(Long tenantId, Long userId, String name, boolean isComponent) {
        Segment segment = new Segment();
        segment.setTenantId(tenantId);
        segment.setName(name);
        segment.setDescription("Test segment: " + name);
        segment.setFilePath("/test/segments/" + name.replaceAll("\\s+", "_") + ".docx");
        segment.setComponent(isComponent);
        segment.setCreatedBy(userId);
        return segmentRepository.save(segment);
    }

    // ── 1. POST /api/segments — Create segment ──

    @Test
    void shouldCreateSegment() throws Exception {
        HttpEntity<MultiValueMap<String, Object>> request = buildCreateRequest(
                tokenA, "Invoice Header", "Header section for invoices");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments",
                HttpMethod.POST,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Invoice Header");
        assertThat(response.getBody().get("description")).isEqualTo("Header section for invoices");
        assertThat(response.getBody().get("id")).isNotNull();
    }

    // ── 2. GET /api/segments — List segments with pagination ──

    @Test
    void shouldListSegmentsWithPagination() {
        createSegmentInDb(tenantAId, userAId, "Seg Alpha", false);
        createSegmentInDb(tenantAId, userAId, "Seg Beta", false);
        createSegmentInDb(tenantAId, userAId, "Seg Gamma", false);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments?page=0&size=2",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("totalElements")).isEqualTo(3);
        assertThat(response.getBody().get("size")).isEqualTo(2);
    }

    // ── 3. GET /api/segments/{id} — Get segment by ID ──

    @Test
    void shouldGetSegmentById() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Detail Segment", false);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Detail Segment");
    }

    @Test
    void shouldReturn404ForNonExistentSegment() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments/999999",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenA)),
                Map.class);

        assertThat(response.getStatusCode().value()).isIn(404, 422);
    }

    // ── 4. PUT /api/segments/{id} — Update segment ──

    @Test
    void shouldUpdateSegment() throws Exception {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Original Name", false);

        Map<String, Object> updateBody = Map.of(
                "name", "Updated Name",
                "description", "Updated description"
        );
        String updateJson = objectMapper.writeValueAsString(updateBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders jsonPartHeaders = new HttpHeaders();
        jsonPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("request", new HttpEntity<>(updateJson, jsonPartHeaders));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Updated Name");
        assertThat(response.getBody().get("description")).isEqualTo("Updated description");
    }

    // ── 5. DELETE /api/segments/{id} — Delete segment ──

    @Test
    void shouldDeleteSegment() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "To Delete", false);

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(tokenA)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(segmentRepository.findById(segment.getId())).isEmpty();
    }

    // ── 6. POST /api/segments/{id}/clone — Clone segment ──

    @Test
    void shouldCloneSegment() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Source Segment", false);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/clone",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("name")).isEqualTo("Source Segment - 副本");
        assertThat(response.getBody().get("id")).isNotNull();
        // Clone should have a different ID
        assertThat(((Number) response.getBody().get("id")).longValue())
                .isNotEqualTo(segment.getId());
    }

    // ── 7. POST /api/segments/{id}/promote — Promote to component ──

    @Test
    void shouldPromoteToComponent() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Regular Segment", false);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/promote",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("component")).isEqualTo(true);
    }

    // ── 8. POST /api/segments/{id}/demote — Demote from component ──

    @Test
    void shouldDemoteFromComponent() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Component Segment", true);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/demote",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenA)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("component")).isEqualTo(false);
    }

    // ── 9. POST /api/segments/{id}/favorite — Add favorite ──

    @Test
    void shouldAddFavorite() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Fav Segment", false);

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/favorite",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenA)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(segmentFavoriteRepository.existsBySegmentIdAndUserId(segment.getId(), userAId))
                .isTrue();
    }

    @Test
    void shouldAddFavoriteIdempotently() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Fav Idempotent", false);

        // Add favorite twice
        restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/favorite",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenA)),
                Void.class);

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/favorite",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenA)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── 10. DELETE /api/segments/{id}/favorite — Remove favorite ──

    @Test
    void shouldRemoveFavorite() {
        Segment segment = createSegmentInDb(tenantAId, userAId, "Unfav Segment", false);

        // Add favorite first
        restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/favorite",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(tokenA)),
                Void.class);

        // Remove favorite
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segment.getId() + "/favorite",
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(tokenA)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(segmentFavoriteRepository.existsBySegmentIdAndUserId(segment.getId(), userAId))
                .isFalse();
    }

    // ── 11. Tenant isolation — segments from one tenant not visible to another ──

    @Test
    void tenantAShouldNotSeeTenantBSegments() {
        createSegmentInDb(tenantAId, userAId, "Tenant A Segment", false);
        createSegmentInDb(tenantBId, userBId, "Tenant B Segment", false);

        // Tenant A lists segments
        ResponseEntity<Map> responseA = restTemplate.exchange(
                baseUrl() + "/api/segments?page=0&size=100",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenA)),
                Map.class);

        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.OK);
        String bodyA = responseA.getBody().toString();
        assertThat(bodyA).contains("Tenant A Segment");
        assertThat(bodyA).doesNotContain("Tenant B Segment");

        // Tenant B lists segments
        ResponseEntity<Map> responseB = restTemplate.exchange(
                baseUrl() + "/api/segments?page=0&size=100",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenB)),
                Map.class);

        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.OK);
        String bodyB = responseB.getBody().toString();
        assertThat(bodyB).contains("Tenant B Segment");
        assertThat(bodyB).doesNotContain("Tenant A Segment");
    }

    @Test
    void tenantBShouldNotAccessTenantASegmentById() {
        Segment segmentA = createSegmentInDb(tenantAId, userAId, "Secret Segment A", false);

        // Tenant B tries to access Tenant A's segment by ID
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments/" + segmentA.getId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenB)),
                Map.class);

        // Should be 404 (not found due to tenant filter) or 422
        assertThat(response.getStatusCode().value()).isIn(404, 422);
    }

    // ── 12. Authentication required ──

    @Test
    void shouldReturnUnauthorizedWithoutToken() {
        HttpHeaders headers = new HttpHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/segments?page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
