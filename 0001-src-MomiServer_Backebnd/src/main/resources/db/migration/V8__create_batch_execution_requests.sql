CREATE TABLE IF NOT EXISTS t_batch_execution_requests (
    id BIGSERIAL PRIMARY KEY,
    batch_definition_id BIGINT NOT NULL,
    schedule_id BIGINT,
    target_agent_id BIGINT NOT NULL,
    requested_type VARCHAR(50) NOT NULL,
    requested_by VARCHAR(255),
    execution_status VARCHAR(50) NOT NULL,
    test_execution BOOLEAN NOT NULL DEFAULT false,
    execution_command VARCHAR(1000) NOT NULL,
    working_directory VARCHAR(500),
    timeout_seconds INTEGER NOT NULL DEFAULT 0,
    max_retry_count INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    log_path VARCHAR(500),
    exit_code INTEGER,
    message TEXT,
    requested_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at timestamp(3),
    started_at timestamp(3),
    finished_at timestamp(3),
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_batch_execution_requests_definition
        FOREIGN KEY (batch_definition_id)
            REFERENCES m_batch_definitions (id),
    CONSTRAINT fk_t_batch_execution_requests_schedule
        FOREIGN KEY (schedule_id)
            REFERENCES m_batch_schedules (id),
    CONSTRAINT chk_t_batch_execution_requests_status
        CHECK (execution_status IN ('queued', 'dispatched', 'running', 'success', 'failed', 'timeout', 'cancelled')),
    CONSTRAINT chk_t_batch_execution_requests_requested_type
        CHECK (requested_type IN ('manual', 'schedule')),
    CONSTRAINT chk_t_batch_execution_requests_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_t_batch_execution_requests_agent_status
    ON t_batch_execution_requests (target_agent_id, execution_status, id);

CREATE INDEX IF NOT EXISTS idx_t_batch_execution_requests_definition_id
    ON t_batch_execution_requests (batch_definition_id);

CREATE INDEX IF NOT EXISTS idx_t_batch_execution_requests_status
    ON t_batch_execution_requests (execution_status);

CREATE OR REPLACE TRIGGER update_t_batch_execution_requests_updated_at
    BEFORE UPDATE ON "t_batch_execution_requests"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
