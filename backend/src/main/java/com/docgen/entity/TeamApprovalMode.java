package com.docgen.entity;

/**
 * How template review participants are constrained for templates owned by the team.
 */
public enum TeamApprovalMode {
    /**
     * Same-team users may be chosen as reviewers (existing behaviour).
     */
    CROSS_REVIEW,
    /**
     * Level 1 reviews must be performed by users with {@code team_review_lane = MAKER};
     * level 2 by {@code CHECKER}.
     */
    MAKER_CHECKER
}
