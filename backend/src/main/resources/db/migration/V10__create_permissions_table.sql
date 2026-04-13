CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id),
    team_id BIGINT REFERENCES teams(id),
    permission_type VARCHAR(20) NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT NOT NULL REFERENCES users(id),
    CHECK (user_id IS NOT NULL OR team_id IS NOT NULL)
);
CREATE INDEX idx_permissions_template_id ON permissions(template_id);
CREATE INDEX idx_permissions_user_id ON permissions(user_id);
CREATE INDEX idx_permissions_team_id ON permissions(team_id);
