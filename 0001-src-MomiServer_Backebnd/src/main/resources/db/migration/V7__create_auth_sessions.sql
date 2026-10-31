CREATE TABLE IF NOT EXISTS t_auth_sessions (
    session_id UUID PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    expires_at timestamp(3) NOT NULL,
    last_used_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT false,
    revoked_at timestamp(3),
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_auth_sessions_user
        FOREIGN KEY (user_email)
            REFERENCES m_users (email)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_t_auth_sessions_user_email
    ON t_auth_sessions (user_email);

CREATE INDEX IF NOT EXISTS idx_t_auth_sessions_expires_at
    ON t_auth_sessions (expires_at);

CREATE INDEX IF NOT EXISTS idx_t_auth_sessions_revoked
    ON t_auth_sessions (revoked);

CREATE OR REPLACE TRIGGER update_t_auth_sessions_updated_at
    BEFORE UPDATE ON "t_auth_sessions"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
