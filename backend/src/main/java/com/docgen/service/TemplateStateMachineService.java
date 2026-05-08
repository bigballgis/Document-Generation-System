package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service managing template lifecycle state transitions.
 * Validates transitions against the allowed state machine and records audit logs.
 * Composite template activation ({@link CompositeTemplateService#activateCompositeTemplate}) uses this service
 * after assembly completeness checks.
 */
@Service
public class TemplateStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(TemplateStateMachineService.class);

    /**
     * Map of allowed transitions: from-state → set of valid target states.
     * DRAFT→ACTIVE is conditionally allowed (only when reviewRequired=false).
     */
    private static final Map<TemplateState, Set<TemplateState>> ALLOWED_TRANSITIONS;

    static {
        Map<TemplateState, Set<TemplateState>> map = new EnumMap<>(TemplateState.class);
        // DRAFT → IN_TEST (submit to test) or → ACTIVE when review is not required
        map.put(TemplateState.DRAFT, EnumSet.of(TemplateState.IN_TEST, TemplateState.ACTIVE));
        // IN_TEST → DRAFT (return to design) or → PENDING_REVIEW (submit for team review)
        map.put(TemplateState.IN_TEST, EnumSet.of(TemplateState.DRAFT, TemplateState.PENDING_REVIEW));
        // PENDING_REVIEW → REVIEWED (after approvals) or → IN_TEST (reject / send back to testing)
        map.put(TemplateState.PENDING_REVIEW, EnumSet.of(TemplateState.REVIEWED, TemplateState.IN_TEST));
        map.put(TemplateState.REVIEWED, EnumSet.of(TemplateState.ACTIVE));
        map.put(TemplateState.ACTIVE, EnumSet.of(TemplateState.ARCHIVED));
        map.put(TemplateState.ARCHIVED, EnumSet.of(TemplateState.DRAFT));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private final TemplateRepository templateRepository;

    public TemplateStateMachineService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * Transition a template to the target state.
     *
     * @param templateId  the template ID
     * @param targetState the desired target state
     * @return the updated template
     * @throws BusinessException if the transition is illegal
     */
    @Transactional
    public Template transition(Long templateId, TemplateState targetState) {
        Template template = findTemplateOrThrow(templateId);
        TemplateState currentState = TemplateState.valueOf(template.getStatus());

        validateTransition(currentState, targetState, template.isReviewRequired());

        String previousStatus = template.getStatus();
        template.setStatus(targetState.name());
        Template saved = templateRepository.save(template);

        // Audit log (placeholder until AuditLogService is implemented in task 16.6)
        log.info("Template state transition: templateId={}, from={}, to={}", templateId, previousStatus, targetState.name());

        return saved;
    }

    /**
     * Get the list of available target states for a template.
     *
     * @param templateId the template ID
     * @return list of valid target states
     */
    @Transactional(readOnly = true)
    public List<TemplateState> getAvailableTransitions(Long templateId) {
        Template template = findTemplateOrThrow(templateId);
        TemplateState currentState = TemplateState.valueOf(template.getStatus());

        Set<TemplateState> targets = ALLOWED_TRANSITIONS.getOrDefault(currentState, Collections.emptySet());
        List<TemplateState> available = new ArrayList<>(targets);

        // DRAFT→ACTIVE is only available when reviewRequired is false
        if (currentState == TemplateState.DRAFT && template.isReviewRequired()) {
            available.remove(TemplateState.ACTIVE);
        }

        return available;
    }

    /**
     * Validate that a transition is allowed.
     */
    void validateTransition(TemplateState from, TemplateState to, boolean reviewRequired) {
        Set<TemplateState> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Collections.emptySet());

        if (!allowed.contains(to)) {
            throw new BusinessException(
                    ErrorCode.TEMPLATE_INVALID_STATE_TRANSITION,
                    String.format("Illegal state transition: %s -> %s", from.name(), to.name()),
                    HttpStatus.BAD_REQUEST);
        }

        // DRAFT→ACTIVE requires reviewRequired=false
        if (from == TemplateState.DRAFT && to == TemplateState.ACTIVE && reviewRequired) {
            throw new BusinessException(
                    ErrorCode.TEMPLATE_REVIEW_REQUIRED,
                    String.format("Review is required for this template; cannot transition from %s to %s directly", from.name(), to.name()),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private Template findTemplateOrThrow(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "Template not found"));
    }
}
