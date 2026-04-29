package com.docgen.service;

import com.docgen.dto.ConditionalApproveRequest;
import com.docgen.dto.SubmitReviewRequest;
import com.docgen.dto.TemplateReviewDTO;
import com.docgen.entity.ReviewStatus;
import com.docgen.entity.Team;
import com.docgen.entity.TeamApprovalMode;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateReview;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.entity.User;
import com.docgen.repository.TeamRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateReviewRepository;
import com.docgen.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateReviewServiceTest {

    @Mock
    private TemplateReviewRepository reviewRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TemplateStateMachineService stateMachineService;

    @Mock
    private AutoActivationService autoActivationService;

    private TemplateReviewService reviewService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reviewService = new TemplateReviewService(
                reviewRepository, templateRepository, userRepository, teamRepository,
                stateMachineService, objectMapper, autoActivationService);
    }


    @Test
    void submitForReview_createsReviewsAndTransitionsState() {
        Template template = createTemplate(1L);
        template.setTeamId(100L);
        template.setStatus("IN_TEST");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        stubTeam(100L, TeamApprovalMode.CROSS_REVIEW);
        when(userRepository.findById(10L)).thenReturn(Optional.of(reviewerUser(10L, 1L, 100L)));
        when(userRepository.findById(20L)).thenReturn(Optional.of(reviewerUser(20L, 1L, 100L)));
        when(stateMachineService.transition(1L, com.docgen.entity.TemplateState.PENDING_REVIEW))
                .thenReturn(template);
        when(reviewRepository.save(any(TemplateReview.class))).thenAnswer(inv -> {
            TemplateReview r = inv.getArgument(0);
            r.setId(100L);
            r.setCreatedAt(Instant.now());
            return r;
        });

        SubmitReviewRequest request = new SubmitReviewRequest(List.of(10L, 20L), 1);
        List<TemplateReviewDTO> result = reviewService.submitForReview(1L, request);

        assertEquals(2, result.size());
        verify(stateMachineService).transition(1L, com.docgen.entity.TemplateState.PENDING_REVIEW);
        verify(reviewRepository, times(2)).save(any(TemplateReview.class));
    }

    @Test
    void submitForReview_templateWithoutTeam_throws() {
        Template template = createTemplate(1L);
        // no team
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        SubmitReviewRequest request = new SubmitReviewRequest(List.of(10L), 1);
        assertThrows(BusinessException.class, () -> reviewService.submitForReview(1L, request));
        verify(stateMachineService, never()).transition(anyLong(), any());
    }

    @Test
    void submitForReview_authorCannotBeReviewer_throws() {
        Template template = createTemplate(1L);
        template.setTeamId(100L);
        template.setCreatedBy(10L);
        template.setStatus("IN_TEST");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        stubTeam(100L, TeamApprovalMode.CROSS_REVIEW);
        when(userRepository.findById(10L)).thenReturn(Optional.of(reviewerUser(10L, 1L, 100L)));

        SubmitReviewRequest request = new SubmitReviewRequest(List.of(10L), 1);
        assertThrows(BusinessException.class, () -> reviewService.submitForReview(1L, request));
        verify(stateMachineService, never()).transition(anyLong(), any());
    }

    @Test
    void submitForReview_notInTest_throws() {
        Template template = createTemplate(1L);
        template.setTeamId(100L);
        template.setStatus("DRAFT");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        stubTeam(100L, TeamApprovalMode.CROSS_REVIEW);
        when(userRepository.findById(10L)).thenReturn(Optional.of(reviewerUser(10L, 1L, 100L)));

        SubmitReviewRequest request = new SubmitReviewRequest(List.of(10L), 1);
        assertThrows(BusinessException.class, () -> reviewService.submitForReview(1L, request));
        verify(stateMachineService, never()).transition(anyLong(), any());
    }

    @Test
    void submitForReview_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        SubmitReviewRequest request = new SubmitReviewRequest(List.of(10L), 1);
        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.submitForReview(999L, request));
    }

    @Test
    void submitForReview_makerChecker_level1_rejectsCheckerLane() {
        Template template = createTemplate(1L);
        template.setTeamId(100L);
        template.setStatus("IN_TEST");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        stubTeam(100L, TeamApprovalMode.MAKER_CHECKER);
        User checker = reviewerUser(10L, 1L, 100L);
        checker.setTeamReviewLane("CHECKER");
        when(userRepository.findById(10L)).thenReturn(Optional.of(checker));

        SubmitReviewRequest request = new SubmitReviewRequest(List.of(10L), 1);
        assertThrows(BusinessException.class, () -> reviewService.submitForReview(1L, request));
        verify(stateMachineService, never()).transition(anyLong(), any());
    }


    @Test
    void approveReview_setsApprovedStatus() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.findByTemplateIdAndReviewLevel(100L, 1))
                .thenReturn(List.of(review));
        when(reviewRepository.existsByTemplateIdAndReviewLevelAndStatus(100L, 2, ReviewStatus.PENDING))
                .thenReturn(false);

        TemplateReviewDTO result = reviewService.approveReview(1L, 10L, "Looks good");

        assertEquals(ReviewStatus.APPROVED, result.getStatus());
        assertEquals("Looks good", result.getComment());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void approveReview_wrongReviewer_throws() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThrows(BusinessException.class,
                () -> reviewService.approveReview(1L, 99L, "comment"));
    }

    @Test
    void approveReview_alreadyCompleted_throws() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        review.setStatus(ReviewStatus.APPROVED);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThrows(BusinessException.class,
                () -> reviewService.approveReview(1L, 10L, "comment"));
    }

    @Test
    void approveReview_allApproved_triggersAutoActivation() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(inv -> {
            TemplateReview r = inv.getArgument(0);
            return r;
        });

        // After save, the review is APPROVED
        TemplateReview savedReview = createPendingReview(1L, 100L, 10L, 1);
        savedReview.setStatus(ReviewStatus.APPROVED);
        when(reviewRepository.findByTemplateIdAndReviewLevel(100L, 1))
                .thenReturn(List.of(savedReview));
        when(reviewRepository.existsByTemplateIdAndReviewLevelAndStatus(100L, 2, ReviewStatus.PENDING))
                .thenReturn(false);

        reviewService.approveReview(1L, 10L, "OK");

        verify(autoActivationService).tryAutoActivate(100L);
    }

    @Test
    void approveReview_nextLevelPending_doesNotTransition() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TemplateReview savedReview = createPendingReview(1L, 100L, 10L, 1);
        savedReview.setStatus(ReviewStatus.APPROVED);
        when(reviewRepository.findByTemplateIdAndReviewLevel(100L, 1))
                .thenReturn(List.of(savedReview));
        when(reviewRepository.existsByTemplateIdAndReviewLevelAndStatus(100L, 2, ReviewStatus.PENDING))
                .thenReturn(true);

        reviewService.approveReview(1L, 10L, "OK");

        // Should NOT trigger auto-activation since level 2 is still pending
        verify(autoActivationService, never()).tryAutoActivate(anyLong());
    }


    @Test
    void conditionalApprove_setsStatusAndSuggestions() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.findByTemplateIdAndReviewLevel(100L, 1))
                .thenReturn(List.of(review));
        when(reviewRepository.existsByTemplateIdAndReviewLevelAndStatus(100L, 2, ReviewStatus.PENDING))
                .thenReturn(false);

        ConditionalApproveRequest request = new ConditionalApproveRequest(
                "Mostly good", List.of("Fix typo on page 2", "Update header"));

        TemplateReviewDTO result = reviewService.conditionalApprove(1L, 10L, request);

        assertEquals(ReviewStatus.CONDITIONAL_APPROVED, result.getStatus());
        assertEquals("Mostly good", result.getComment());
        assertEquals(2, result.getSuggestions().size());
    }


    @Test
    void rejectReview_setsRejectedAndTransitionsToInTest() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachineService.transition(100L, com.docgen.entity.TemplateState.IN_TEST))
                .thenReturn(new Template());

        TemplateReviewDTO result = reviewService.rejectReview(1L, 10L, "Needs major rework");

        assertEquals(ReviewStatus.REJECTED, result.getStatus());
        assertEquals("Needs major rework", result.getComment());
        verify(stateMachineService).transition(100L, com.docgen.entity.TemplateState.IN_TEST);
    }

    @Test
    void rejectReview_emptyReason_throws() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThrows(BusinessException.class,
                () -> reviewService.rejectReview(1L, 10L, ""));
    }

    @Test
    void rejectReview_nullReason_throws() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThrows(BusinessException.class,
                () -> reviewService.rejectReview(1L, 10L, null));
    }

    @Test
    void rejectReview_reviewNotFound_throws() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.rejectReview(999L, 10L, "reason"));
    }


    @Test
    void getReview_returnsDTO() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        TemplateReviewDTO result = reviewService.getReview(1L);

        assertEquals(1L, result.getId());
        assertEquals(100L, result.getTemplateId());
        assertEquals(ReviewStatus.PENDING, result.getStatus());
    }


    @Test
    void getOnlyOfficeReviewUrl_returnsUrl() {
        Template template = createTemplate(1L);
        template.setTemplateFilePath("templates/test.docx");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        String url = reviewService.getOnlyOfficeReviewUrl(1L);

        assertTrue(url.contains("templates/test.docx"));
        assertTrue(url.contains("mode=review"));
    }


    @Test
    void approveReview_partialReviewsIncomplete_doesNotCallAutoActivation() {
        TemplateReview review1 = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review1));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // One approved, one still pending at same level
        TemplateReview approved = createPendingReview(1L, 100L, 10L, 1);
        approved.setStatus(ReviewStatus.APPROVED);
        TemplateReview pending = createPendingReview(2L, 100L, 20L, 1);
        when(reviewRepository.findByTemplateIdAndReviewLevel(100L, 1))
                .thenReturn(List.of(approved, pending));

        reviewService.approveReview(1L, 10L, "OK");

        verify(autoActivationService, never()).tryAutoActivate(anyLong());
    }

    @Test
    void conditionalApprove_allCompleted_noNextLevel_triggersAutoActivation() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TemplateReview conditionallyApproved = createPendingReview(1L, 100L, 10L, 1);
        conditionallyApproved.setStatus(ReviewStatus.CONDITIONAL_APPROVED);
        when(reviewRepository.findByTemplateIdAndReviewLevel(100L, 1))
                .thenReturn(List.of(conditionallyApproved));
        when(reviewRepository.existsByTemplateIdAndReviewLevelAndStatus(100L, 2, ReviewStatus.PENDING))
                .thenReturn(false);

        com.docgen.dto.ConditionalApproveRequest request =
                new com.docgen.dto.ConditionalApproveRequest("OK with changes", List.of("Fix typo"));
        reviewService.conditionalApprove(1L, 10L, request);

        verify(autoActivationService).tryAutoActivate(100L);
    }

    @Test
    void approveReview_nextLevelExists_doesNotCallAutoActivation() {
        TemplateReview review = createPendingReview(1L, 100L, 10L, 1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TemplateReview approved = createPendingReview(1L, 100L, 10L, 1);
        approved.setStatus(ReviewStatus.APPROVED);
        when(reviewRepository.findByTemplateIdAndReviewLevel(100L, 1))
                .thenReturn(List.of(approved));
        when(reviewRepository.existsByTemplateIdAndReviewLevelAndStatus(100L, 2, ReviewStatus.PENDING))
                .thenReturn(true);

        reviewService.approveReview(1L, 10L, "OK");

        verify(autoActivationService, never()).tryAutoActivate(anyLong());
    }


    private Template createTemplate(Long id) {
        Template t = new Template();
        t.setId(id);
        t.setTenantId(1L);
        t.setName("Test Template");
        t.setTemplateFilePath("templates/test.docx");
        t.setCreatedBy(1L);
        t.setStatus("DRAFT");
        return t;
    }

    private void stubTeam(long teamId, TeamApprovalMode mode) {
        Team team = new Team();
        team.setId(teamId);
        team.setApprovalMode(mode);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
    }

    private static User reviewerUser(long id, long tenantId, long teamId) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setTeamId(teamId);
        u.setUsername("u" + id);
        u.setEmail("u" + id + "@t.com");
        u.setRole("USER");
        return u;
    }

    private TemplateReview createPendingReview(Long id, Long templateId, Long reviewerId, int level) {
        TemplateReview review = new TemplateReview();
        review.setId(id);
        review.setTemplateId(templateId);
        review.setReviewerId(reviewerId);
        review.setReviewLevel(level);
        review.setStatus(ReviewStatus.PENDING);
        review.setCreatedAt(Instant.now());
        return review;
    }
}

