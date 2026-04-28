package com.docgen.service;

import com.docgen.dto.ConditionalApproveRequest;
import com.docgen.dto.SubmitReviewRequest;
import com.docgen.dto.TemplateReviewDTO;
import com.docgen.entity.ReviewStatus;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateReview;
import com.docgen.entity.TemplateState;
import com.docgen.entity.User;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateReviewRepository;
import com.docgen.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service handling template review workflow.
 * Supports multi-level review (initial → final), approve, conditional approve, and reject.
 */
@Service
public class TemplateReviewService {

    private static final Logger log = LoggerFactory.getLogger(TemplateReviewService.class);

    private final TemplateReviewRepository reviewRepository;
    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final TemplateStateMachineService stateMachineService;
    private final ObjectMapper objectMapper;
    private final AutoActivationService autoActivationService;

    public TemplateReviewService(TemplateReviewRepository reviewRepository,
                                 TemplateRepository templateRepository,
                                 UserRepository userRepository,
                                 TemplateStateMachineService stateMachineService,
                                 ObjectMapper objectMapper,
                                 AutoActivationService autoActivationService) {
        this.reviewRepository = reviewRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.stateMachineService = stateMachineService;
        this.objectMapper = objectMapper;
        this.autoActivationService = autoActivationService;
    }

    /**
     * Submit a template for review. Creates review records for each reviewer.
     * Transitions the template to PENDING_REVIEW state.
     */
    @Transactional
    public List<TemplateReviewDTO> submitForReview(Long templateId, SubmitReviewRequest request) {
        Template template = findTemplateOrThrow(templateId);
        assertReviewersAllowedForTemplate(template, request.getReviewerIds());

        // Transition template to PENDING_REVIEW
        stateMachineService.transition(templateId, TemplateState.PENDING_REVIEW);

        List<TemplateReview> reviews = new ArrayList<>();
        for (Long reviewerId : request.getReviewerIds()) {
            TemplateReview review = new TemplateReview();
            review.setTemplateId(templateId);
            review.setReviewerId(reviewerId);
            review.setReviewLevel(request.getReviewLevel());
            review.setStatus(ReviewStatus.PENDING);
            reviews.add(reviewRepository.save(review));
        }

        log.info("Review submitted: templateId={}, reviewerIds={}, level={}",
                templateId, request.getReviewerIds(), request.getReviewLevel());
        return reviews.stream().map(this::toDTO).toList();
    }

    /**
     * Approve a review. If all reviews at the current level are approved and it's the
     * final level, transitions the template to REVIEWED state.
     */
    @Transactional
    public TemplateReviewDTO approveReview(Long reviewId, Long reviewerId, String comment) {
        TemplateReview review = findReviewOrThrow(reviewId);
        validateReviewerAndPending(review, reviewerId);

        review.setStatus(ReviewStatus.APPROVED);
        review.setComment(comment);
        review.setCompletedAt(Instant.now());
        TemplateReview saved = reviewRepository.save(review);

        log.info("Review approved: reviewId={}, templateId={}", reviewId, review.getTemplateId());
        checkAndTransitionTemplate(review.getTemplateId(), review.getReviewLevel());

        return toDTO(saved);
    }

    /**
     * Conditionally approve a review with suggestions for modification.
     */
    @Transactional
    public TemplateReviewDTO conditionalApprove(Long reviewId, Long reviewerId,
                                                 ConditionalApproveRequest request) {
        TemplateReview review = findReviewOrThrow(reviewId);
        validateReviewerAndPending(review, reviewerId);

        review.setStatus(ReviewStatus.CONDITIONAL_APPROVED);
        review.setComment(request.getComment());
        review.setSuggestionsJson(toJson(request.getSuggestions()));
        review.setCompletedAt(Instant.now());
        TemplateReview saved = reviewRepository.save(review);

        log.info("Review conditionally approved: reviewId={}, templateId={}", reviewId, review.getTemplateId());
        checkAndTransitionTemplate(review.getTemplateId(), review.getReviewLevel());

        return toDTO(saved);
    }

    /**
     * Reject a review. Transitions the template back to DRAFT state.
     */
    @Transactional
    public TemplateReviewDTO rejectReview(Long reviewId, Long reviewerId, String reason) {
        TemplateReview review = findReviewOrThrow(reviewId);
        validateReviewerAndPending(review, reviewerId);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "驳回原因不能为空", HttpStatus.BAD_REQUEST);
        }

        review.setStatus(ReviewStatus.REJECTED);
        review.setComment(reason);
        review.setCompletedAt(Instant.now());
        TemplateReview saved = reviewRepository.save(review);

        // Transition template back to DRAFT
        stateMachineService.transition(review.getTemplateId(), TemplateState.DRAFT);

        log.info("Review rejected: reviewId={}, templateId={}, reason={}",
                reviewId, review.getTemplateId(), reason);
        return toDTO(saved);
    }

    /**
     * List reviews for a specific template.
     */
    @Transactional(readOnly = true)
    public Page<TemplateReviewDTO> listReviewsByTemplate(Long templateId, Pageable pageable) {
        findTemplateOrThrow(templateId);
        return reviewRepository.findByTemplateId(templateId, pageable).map(this::toDTO);
    }

    /**
     * List all reviews globally, optionally filtered by status.
     */
    @Transactional(readOnly = true)
    public Page<TemplateReviewDTO> listAllReviews(ReviewStatus status, Pageable pageable) {
        if (status != null) {
            return reviewRepository.findByStatus(status, pageable).map(this::toDTO);
        }
        return reviewRepository.findAll(pageable).map(this::toDTO);
    }

    /**
     * List reviews assigned to a specific reviewer, optionally filtered by status.
     */
    @Transactional(readOnly = true)
    public Page<TemplateReviewDTO> listReviewsByReviewer(Long reviewerId, ReviewStatus status, Pageable pageable) {
        if (status != null) {
            return reviewRepository.findByReviewerIdAndStatus(reviewerId, status, pageable).map(this::toDTO);
        }
        return reviewRepository.findByReviewerId(reviewerId, pageable).map(this::toDTO);
    }

    /**
     * Get a single review by ID.
     */
    @Transactional(readOnly = true)
    public TemplateReviewDTO getReview(Long reviewId) {
        return toDTO(findReviewOrThrow(reviewId));
    }

    /**
     * Get the OnlyOffice callback URL for review annotations.
     * Returns the OnlyOffice editor URL with review mode enabled.
     */
    public String getOnlyOfficeReviewUrl(Long templateId) {
        Template template = findTemplateOrThrow(templateId);
        // OnlyOffice integration: return editor URL with review/comment mode
        return String.format("/onlyoffice/editor?file=%s&mode=review",
                template.getTemplateFilePath());
    }

    // ── Private helpers ──

    /**
     * After a review action, check if all reviews at the given level are completed
     * (approved or conditionally approved). If so, and it's the highest level,
     * transition the template to REVIEWED.
     */
    private void checkAndTransitionTemplate(Long templateId, int reviewLevel) {
        List<TemplateReview> levelReviews = reviewRepository
                .findByTemplateIdAndReviewLevel(templateId, reviewLevel);

        boolean allCompleted = levelReviews.stream()
                .allMatch(r -> r.getStatus() == ReviewStatus.APPROVED
                        || r.getStatus() == ReviewStatus.CONDITIONAL_APPROVED);

        if (!allCompleted) {
            return;
        }

        // Check if there's a next level pending
        boolean nextLevelPending = reviewRepository
                .existsByTemplateIdAndReviewLevelAndStatus(templateId, reviewLevel + 1, ReviewStatus.PENDING);

        if (!nextLevelPending) {
            // All levels completed — auto-activate (PENDING_REVIEW → REVIEWED → ACTIVE + API Key)
            autoActivationService.tryAutoActivate(templateId);
            log.info("All review levels completed, auto-activation triggered for template {}", templateId);
        }
    }

    /**
     * Enforces same-tenant, same-team reviewers; template must have a team; author cannot review own template.
     */
    private void assertReviewersAllowedForTemplate(Template template, List<Long> reviewerIds) {
        if (template.getTeamId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Template must be assigned to a team before submitting for review",
                    HttpStatus.BAD_REQUEST);
        }
        Long templateTenantId = template.getTenantId();
        Long teamId = template.getTeamId();
        Long createdBy = template.getCreatedBy();
        for (Long reviewerId : reviewerIds) {
            User u = userRepository.findById(reviewerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "Reviewer user not found", HttpStatus.BAD_REQUEST));
            if (!u.getTenantId().equals(templateTenantId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Reviewer must belong to the same tenant as the template", HttpStatus.BAD_REQUEST);
            }
            if (u.getTeamId() == null || !u.getTeamId().equals(teamId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Reviewer must belong to the template's team", HttpStatus.BAD_REQUEST);
            }
            if (createdBy != null && u.getId().equals(createdBy)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Template author cannot be a reviewer", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateReviewerAndPending(TemplateReview review, Long reviewerId) {
        if (!review.getReviewerId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AUTHORIZED,
                    "只有指定的审查人才能执行此操作", HttpStatus.FORBIDDEN);
        }
        if (review.getStatus() != ReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_COMPLETED,
                    "该审查已完成，不能重复操作", HttpStatus.BAD_REQUEST);
        }
    }

    private TemplateReview findReviewOrThrow(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.REVIEW_NOT_FOUND, "审查记录不存在"));
    }

    private Template findTemplateOrThrow(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));
    }

    TemplateReviewDTO toDTO(TemplateReview review) {
        List<String> suggestions = parseSuggestions(review.getSuggestionsJson());
        return new TemplateReviewDTO(
                review.getId(),
                review.getTemplateId(),
                review.getReviewerId(),
                review.getReviewLevel(),
                review.getStatus(),
                review.getComment(),
                suggestions,
                review.getCreatedAt(),
                review.getCompletedAt()
        );
    }

    private List<String> parseSuggestions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse suggestions JSON: {}", json, e);
            return Collections.emptyList();
        }
    }

    private String toJson(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "序列化建议列表失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
