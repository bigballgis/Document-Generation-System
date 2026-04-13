package com.docgen.property;

import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.service.TemplateStateMachineService;
import net.jqwik.api.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for template state machine transition legality.
 *
 * <p><b>Validates: Requirements 52.1, 52.2, 52.3</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 15: 模板状态机转换合法性")
class TemplateStateMachinePropertyTest {

    /**
     * The complete set of legal transitions as defined in the state machine.
     * DRAFT→ACTIVE is conditionally legal (only when reviewRequired=false).
     */
    private static final Map<TemplateState, Set<TemplateState>> LEGAL_TRANSITIONS;

    static {
        Map<TemplateState, Set<TemplateState>> map = new EnumMap<>(TemplateState.class);
        map.put(TemplateState.DRAFT, EnumSet.of(TemplateState.PENDING_REVIEW, TemplateState.ACTIVE));
        map.put(TemplateState.PENDING_REVIEW, EnumSet.of(TemplateState.REVIEWED, TemplateState.DRAFT));
        map.put(TemplateState.REVIEWED, EnumSet.of(TemplateState.ACTIVE));
        map.put(TemplateState.ACTIVE, EnumSet.of(TemplateState.ARCHIVED));
        map.put(TemplateState.ARCHIVED, EnumSet.of(TemplateState.DRAFT));
        LEGAL_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private final TemplateStateMachineService stateMachineService;
    private final Method validateTransitionMethod;

    TemplateStateMachinePropertyTest() throws Exception {
        // Instantiate service with null repository — validateTransition does not use it
        stateMachineService = new TemplateStateMachineService(null);

        // Access the package-private validateTransition method via reflection
        validateTransitionMethod = TemplateStateMachineService.class.getDeclaredMethod(
                "validateTransition", TemplateState.class, TemplateState.class, boolean.class);
        validateTransitionMethod.setAccessible(true);
    }

    /**
     * Invokes the package-private validateTransition via reflection.
     * Unwraps InvocationTargetException to rethrow the actual cause.
     */
    private void invokeValidateTransition(TemplateState from, TemplateState to, boolean reviewRequired)
            throws Throwable {
        try {
            validateTransitionMethod.invoke(stateMachineService, from, to, reviewRequired);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Property 15: For any (from, to) state pair, if the transition is in the
     * predefined legal set, validateTransition should NOT throw; if it is not,
     * validateTransition should throw BusinessException.
     *
     * reviewRequired is set to false so that DRAFT→ACTIVE is allowed as a
     * normal legal transition.
     */
    @Property(tries = 100)
    void legalTransitionsAccepted_illegalTransitionsRejected(
            @ForAll("allStatePairs") StatePair pair
    ) {
        TemplateState from = pair.from();
        TemplateState to = pair.to();
        boolean reviewRequired = false;

        Set<TemplateState> allowed = LEGAL_TRANSITIONS.getOrDefault(from, Collections.emptySet());
        boolean isLegal = allowed.contains(to);

        if (isLegal) {
            assertDoesNotThrow(
                    () -> invokeValidateTransition(from, to, reviewRequired),
                    String.format("Legal transition %s → %s should be accepted", from, to));
        } else {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> { invokeValidateTransition(from, to, reviewRequired); },
                    String.format("Illegal transition %s → %s should be rejected", from, to));
            assertEquals("TEMPLATE_INVALID_STATE_TRANSITION", ex.getErrorCode());
        }
    }

    /**
     * Property 15 (special case): DRAFT→ACTIVE with reviewRequired=true must
     * be rejected, while reviewRequired=false must be accepted.
     */
    @Property(tries = 50)
    void draftToActiveRespectsReviewRequired(
            @ForAll("reviewRequiredFlags") boolean reviewRequired
    ) {
        TemplateState from = TemplateState.DRAFT;
        TemplateState to = TemplateState.ACTIVE;

        if (reviewRequired) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> { invokeValidateTransition(from, to, true); },
                    "DRAFT → ACTIVE with reviewRequired=true should be rejected");
            assertEquals("TEMPLATE_REVIEW_REQUIRED", ex.getErrorCode());
        } else {
            assertDoesNotThrow(
                    () -> invokeValidateTransition(from, to, false),
                    "DRAFT → ACTIVE with reviewRequired=false should be accepted");
        }
    }

    // ── Generators ──

    @Provide
    Arbitrary<StatePair> allStatePairs() {
        Arbitrary<TemplateState> states = Arbitraries.of(TemplateState.values());
        return Combinators.combine(states, states).as(StatePair::new);
    }

    @Provide
    Arbitrary<Boolean> reviewRequiredFlags() {
        return Arbitraries.of(true, false);
    }

    // ── Helper record ──

    record StatePair(TemplateState from, TemplateState to) {}
}
