CREATE TABLE t_agent_mqtt_credentials (
    agent_id UUID NOT NULL REFERENCES m_agents(id),
    generation BIGINT NOT NULL,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    encrypted_password TEXT NOT NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (sync_status IN ('pending', 'ready', 'revoked')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(agent_id, generation)
);
ALTER TABLE m_agents ADD COLUMN heartbeat_at TIMESTAMPTZ;
ALTER TABLE m_agents ADD COLUMN mqtt_connected BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE t_batch_execution_requests ADD COLUMN stdout TEXT;
ALTER TABLE t_batch_execution_requests ADD COLUMN stderr TEXT;
ALTER TABLE t_batch_execution_requests ADD COLUMN output_truncated BOOLEAN NOT NULL DEFAULT false;
