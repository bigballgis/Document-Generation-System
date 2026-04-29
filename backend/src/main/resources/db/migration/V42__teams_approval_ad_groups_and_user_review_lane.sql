-- Team template review mode and optional Azure AD group bindings (sync to be implemented separately).
ALTER TABLE teams
    ADD COLUMN approval_mode VARCHAR(32) NOT NULL DEFAULT 'CROSS_REVIEW',
    ADD COLUMN ad_group_object_id VARCHAR(128),
    ADD COLUMN ad_maker_group_object_id VARCHAR(128),
    ADD COLUMN ad_checker_group_object_id VARCHAR(128);

COMMENT ON COLUMN teams.approval_mode IS 'CROSS_REVIEW: any team member may be selected as reviewer; MAKER_CHECKER: level-1 makers, level-2 checkers via team_review_lane on users.';
COMMENT ON COLUMN teams.ad_group_object_id IS 'Azure AD group object id for membership sync in cross-review mode.';
COMMENT ON COLUMN teams.ad_maker_group_object_id IS 'Azure AD group object id for maker role in maker-checker mode.';
COMMENT ON COLUMN teams.ad_checker_group_object_id IS 'Azure AD group object id for checker role in maker-checker mode.';

-- Per-user lane for maker-checker teams (set by admin or future AD sync job).
ALTER TABLE users
    ADD COLUMN team_review_lane VARCHAR(20);

COMMENT ON COLUMN users.team_review_lane IS 'MAKER or CHECKER when team uses MAKER_CHECKER approval; null otherwise.';
