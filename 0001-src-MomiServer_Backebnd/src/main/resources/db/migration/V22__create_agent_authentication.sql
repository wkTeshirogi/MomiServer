CREATE TABLE m_agents (
    id UUID PRIMARY KEY,
    device_id BIGINT NOT NULL UNIQUE REFERENCES m_devices(id),
    generation BIGINT NOT NULL DEFAULT 1 CHECK (generation > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'registered', 'disabled')),
    mqtt_client_id VARCHAR(100) NOT NULL UNIQUE,
    code_hash VARCHAR(64) UNIQUE,
    code_expires_at TIMESTAMPTZ,
    registration_request_id UUID UNIQUE,
    public_key TEXT,
    registration_response TEXT,
    recovery_expires_at TIMESTAMPTZ,
    hostname VARCHAR(255),
    os VARCHAR(40),
    agent_version VARCHAR(40),
    registered_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_agent_sessions (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES m_agents(id),
    generation BIGINT NOT NULL,
    access_hash VARCHAR(64) NOT NULL UNIQUE,
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    access_expires_at TIMESTAMPTZ NOT NULL,
    refresh_expires_at TIMESTAMPTZ NOT NULL,
    previous_refresh_hash VARCHAR(64),
    refresh_request_id UUID,
    refresh_response TEXT,
    recovery_expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_agent_sessions_agent ON t_agent_sessions(agent_id);
CREATE INDEX idx_agent_sessions_previous_refresh ON t_agent_sessions(previous_refresh_hash);

CREATE TABLE t_agent_auth_challenges (
    nonce_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE t_agent_auth_rate_limits (
    bucket_key VARCHAR(64) NOT NULL,
    window_minute BIGINT NOT NULL,
    attempts INTEGER NOT NULL,
    PRIMARY KEY (bucket_key, window_minute)
);

CREATE TABLE t_agent_auth_audit_events (
    id BIGSERIAL PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES m_agents(id),
    generation BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Existing requests have no trustworthy registration generation. Do not grant them
-- to a newly registered/replacement machine by backfilling this column.
ALTER TABLE t_batch_execution_requests ADD COLUMN target_agent_generation BIGINT;
