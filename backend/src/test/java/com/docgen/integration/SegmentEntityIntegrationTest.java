package com.docgen.integration;

import com.docgen.entity.*;
import com.docgen.repository.*;
import com.docgen.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for segment-related entities, Flyway migrations V29-V35,
 * CRUD operations, unique/foreign key constraints, and Hibernate tenantFilter isolation.
 * Validates: Requirements 1.3, 2.10
 */
class SegmentEntityIntegrationTest extends BaseIntegrationTest {

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
    private SegmentReviewRepository segmentReviewRepository;

    @Autowired
    private SegmentTestDataRepository segmentTestDataRepository;

    @Autowired
    private SegmentFavoriteRepository segmentFavoriteRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long tenantAId;
    private Long tenantBId;
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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        segmentFavoriteRepository.deleteAll();
        segmentTestDataRepository.deleteAll();
        segmentReviewRepository.deleteAll();
        segmentTagMappingRepository.deleteAll();
        segmentVersionRepository.deleteAll();
        segmentRepository.deleteAll();
        templateRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }


    // ── 1. Flyway Migrations V29-V35 ──

    @Test
    void flywayMigrationsShouldRunSuccessfully() {
        // If the Spring context loads and Flyway runs without error, all migrations succeeded.
        // Verify key tables exist by performing simple queries.
        assertThat(segmentRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(segmentVersionRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(segmentTagMappingRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(segmentReviewRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(segmentTestDataRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(segmentFavoriteRepository.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void templatesShouldHaveTemplateTypeColumn() {
        // V29 adds template_type column with default 'SINGLE'
        Template template = new Template();
        template.setTenantId(tenantAId);
        template.setName("Migration Test Template");
        template.setDescription("Testing V29 migration");
        template.setTemplateFilePath("/test/migration.docx");
        template.setCreatedBy(userAId);
        template = templateRepository.save(template);

        assertThat(template.getTemplateType()).isEqualTo("SINGLE");
        assertThat(template.getAssemblyConfig()).isNull();
    }

    // ── 2. Segment CRUD ──

    @Test
    void shouldCreateAndReadSegment() {
        Segment segment = createSegment(tenantAId, userAId, "Test Segment", false);

        Optional<Segment> found = segmentRepository.findById(segment.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Segment");
        assertThat(found.get().getTenantId()).isEqualTo(tenantAId);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateSegment() {
        Segment segment = createSegment(tenantAId, userAId, "Original Name", false);

        segment.setName("Updated Name");
        segment.setDescription("Updated description");
        segmentRepository.save(segment);

        Segment updated = segmentRepository.findById(segment.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void shouldDeleteSegment() {
        Segment segment = createSegment(tenantAId, userAId, "To Delete", false);
        Long id = segment.getId();

        segmentRepository.deleteById(id);

        assertThat(segmentRepository.findById(id)).isEmpty();
    }

    @Test
    void shouldListSegmentsByTenantId() {
        createSegment(tenantAId, userAId, "Seg A1", false);
        createSegment(tenantAId, userAId, "Seg A2", true);
        createSegment(tenantBId, userBId, "Seg B1", false);

        List<Segment> tenantASegments = segmentRepository.findByTenantIdAndComponent(tenantAId, false);
        assertThat(tenantASegments).hasSize(1);
        assertThat(tenantASegments.get(0).getName()).isEqualTo("Seg A1");

        List<Segment> tenantAComponents = segmentRepository.findByTenantIdAndComponent(tenantAId, true);
        assertThat(tenantAComponents).hasSize(1);
    }

    // ── 3. SegmentVersion Unique Constraint (segment_id + version_number) ──

    @Test
    void shouldCreateSegmentVersion() {
        Segment segment = createSegment(tenantAId, userAId, "Versioned Seg", false);

        SegmentVersion v1 = new SegmentVersion();
        v1.setSegmentId(segment.getId());
        v1.setVersionNumber(1);
        v1.setFilePath("/versions/v1.docx");
        v1.setCreatedBy(userAId);
        segmentVersionRepository.save(v1);

        List<SegmentVersion> versions = segmentVersionRepository
                .findBySegmentIdOrderByVersionNumberDesc(segment.getId());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void shouldEnforceSegmentVersionUniqueConstraint() {
        Segment segment = createSegment(tenantAId, userAId, "Unique Constraint Seg", false);

        SegmentVersion v1 = new SegmentVersion();
        v1.setSegmentId(segment.getId());
        v1.setVersionNumber(1);
        v1.setFilePath("/versions/v1.docx");
        v1.setCreatedBy(userAId);
        segmentVersionRepository.saveAndFlush(v1);

        SegmentVersion duplicate = new SegmentVersion();
        duplicate.setSegmentId(segment.getId());
        duplicate.setVersionNumber(1); // same segment_id + version_number
        duplicate.setFilePath("/versions/v1_dup.docx");
        duplicate.setCreatedBy(userAId);

        assertThatThrownBy(() -> segmentVersionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── 4. SegmentFavorite Unique Constraint (segment_id + user_id) ──

    @Test
    void shouldCreateSegmentFavorite() {
        Segment segment = createSegment(tenantAId, userAId, "Fav Seg", false);

        SegmentFavorite fav = new SegmentFavorite();
        fav.setSegmentId(segment.getId());
        fav.setUserId(userAId);
        segmentFavoriteRepository.save(fav);

        assertThat(segmentFavoriteRepository.existsBySegmentIdAndUserId(segment.getId(), userAId)).isTrue();
    }

    @Test
    void shouldEnforceSegmentFavoriteUniqueConstraint() {
        Segment segment = createSegment(tenantAId, userAId, "Fav Unique Seg", false);

        SegmentFavorite fav1 = new SegmentFavorite();
        fav1.setSegmentId(segment.getId());
        fav1.setUserId(userAId);
        segmentFavoriteRepository.saveAndFlush(fav1);

        SegmentFavorite duplicate = new SegmentFavorite();
        duplicate.setSegmentId(segment.getId());
        duplicate.setUserId(userAId); // same segment_id + user_id

        assertThatThrownBy(() -> segmentFavoriteRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── 5. SegmentTagMapping Unique Constraint (segment_id + tag_id) ──

    @Test
    void shouldEnforceSegmentTagMappingUniqueConstraint() {
        Segment segment = createSegment(tenantAId, userAId, "Tag Unique Seg", false);

        // We need a tag — insert directly via native query since TemplateTag entity may not be available
        Long tagId = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO template_tags (tenant_id, name) VALUES (:tenantId, :name)")
                    .setParameter("tenantId", tenantAId)
                    .setParameter("name", "test-tag-" + System.nanoTime())
                    .executeUpdate();
            return ((Number) entityManager.createNativeQuery(
                    "SELECT currval('template_tags_id_seq')").getSingleResult()).longValue();
        });

        SegmentTagMapping mapping1 = new SegmentTagMapping(segment.getId(), tagId);
        segmentTagMappingRepository.saveAndFlush(mapping1);

        SegmentTagMapping duplicate = new SegmentTagMapping(segment.getId(), tagId);

        assertThatThrownBy(() -> segmentTagMappingRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


    // ── 6. Foreign Key Constraints ──

    @Test
    void shouldEnforceForeignKeyOnSegmentVersion() {
        SegmentVersion orphan = new SegmentVersion();
        orphan.setSegmentId(999999L); // non-existent segment
        orphan.setVersionNumber(1);
        orphan.setFilePath("/orphan/v1.docx");
        orphan.setCreatedBy(userAId);

        assertThatThrownBy(() -> segmentVersionRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldEnforceForeignKeyOnSegmentFavorite() {
        SegmentFavorite orphan = new SegmentFavorite();
        orphan.setSegmentId(999999L); // non-existent segment
        orphan.setUserId(userAId);

        assertThatThrownBy(() -> segmentFavoriteRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldCascadeDeleteSegmentVersionsWhenSegmentDeleted() {
        Segment segment = createSegment(tenantAId, userAId, "Cascade Seg", false);

        SegmentVersion v1 = new SegmentVersion();
        v1.setSegmentId(segment.getId());
        v1.setVersionNumber(1);
        v1.setFilePath("/cascade/v1.docx");
        v1.setCreatedBy(userAId);
        segmentVersionRepository.saveAndFlush(v1);

        assertThat(segmentVersionRepository.findBySegmentIdOrderByVersionNumberDesc(segment.getId())).hasSize(1);

        // Delete segment — versions should cascade delete
        segmentRepository.deleteById(segment.getId());
        segmentRepository.flush();

        assertThat(segmentVersionRepository.findBySegmentIdOrderByVersionNumberDesc(segment.getId())).isEmpty();
    }

    @Test
    void shouldCascadeDeleteFavoritesWhenSegmentDeleted() {
        Segment segment = createSegment(tenantAId, userAId, "Cascade Fav Seg", false);

        SegmentFavorite fav = new SegmentFavorite();
        fav.setSegmentId(segment.getId());
        fav.setUserId(userAId);
        segmentFavoriteRepository.saveAndFlush(fav);

        assertThat(segmentFavoriteRepository.existsBySegmentIdAndUserId(segment.getId(), userAId)).isTrue();

        segmentRepository.deleteById(segment.getId());
        segmentRepository.flush();

        assertThat(segmentFavoriteRepository.existsBySegmentIdAndUserId(segment.getId(), userAId)).isFalse();
    }

    // ── 7. Hibernate tenantFilter Isolation ──

    @Test
    void tenantFilterShouldIsolateSegmentsByTenantId() {
        // Create segments for both tenants
        createSegment(tenantAId, userAId, "Tenant A Segment 1", false);
        createSegment(tenantAId, userAId, "Tenant A Segment 2", true);
        createSegment(tenantBId, userBId, "Tenant B Segment 1", false);

        // Enable tenant filter for Tenant A and query
        transactionTemplate.execute(status -> {
            TenantContext.setCurrentTenantId(tenantAId);
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantAId);

            @SuppressWarnings("unchecked")
            List<Segment> results = entityManager
                    .createQuery("SELECT s FROM Segment s", Segment.class)
                    .getResultList();

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(s -> s.getTenantId().equals(tenantAId));
            assertThat(results).noneMatch(s -> s.getName().contains("Tenant B"));

            return null;
        });

        // Enable tenant filter for Tenant B and query
        transactionTemplate.execute(status -> {
            TenantContext.setCurrentTenantId(tenantBId);
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantBId);

            @SuppressWarnings("unchecked")
            List<Segment> results = entityManager
                    .createQuery("SELECT s FROM Segment s", Segment.class)
                    .getResultList();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTenantId()).isEqualTo(tenantBId);
            assertThat(results.get(0).getName()).isEqualTo("Tenant B Segment 1");

            return null;
        });
    }

    @Test
    void tenantFilterShouldReturnEmptyForTenantWithNoSegments() {
        createSegment(tenantAId, userAId, "Only Tenant A", false);

        transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantBId);

            @SuppressWarnings("unchecked")
            List<Segment> results = entityManager
                    .createQuery("SELECT s FROM Segment s", Segment.class)
                    .getResultList();

            assertThat(results).isEmpty();
            return null;
        });
    }

    // ── Helper Methods ──

    private Segment createSegment(Long tenantId, Long userId, String name, boolean isComponent) {
        Segment segment = new Segment();
        segment.setTenantId(tenantId);
        segment.setName(name);
        segment.setDescription("Test segment: " + name);
        segment.setFilePath("/test/segments/" + name.replaceAll("\\s+", "_") + ".docx");
        segment.setComponent(isComponent);
        segment.setCreatedBy(userId);
        return segmentRepository.save(segment);
    }
}
