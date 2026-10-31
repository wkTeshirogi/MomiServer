CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE t_batch_execution_requests
    ADD COLUMN IF NOT EXISTS execution_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS scheduled_fire_time timestamp(3),
    ADD COLUMN IF NOT EXISTS dispatch_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_dispatch_attempt_at timestamp(3);

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_batch_execution_requests_execution_id
    ON t_batch_execution_requests (execution_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_batch_execution_requests_schedule_fire
    ON t_batch_execution_requests (schedule_id, scheduled_fire_time)
    WHERE schedule_id IS NOT NULL AND scheduled_fire_time IS NOT NULL;

CREATE TABLE IF NOT EXISTS t_batch_execution_files (
    id BIGSERIAL PRIMARY KEY,
    execution_request_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_batch_execution_files_request
        FOREIGN KEY (execution_request_id)
            REFERENCES t_batch_execution_requests (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_t_batch_execution_files_execution_request_id
    ON t_batch_execution_files (execution_request_id);

CREATE OR REPLACE TRIGGER update_t_batch_execution_files_updated_at
    BEFORE UPDATE ON "t_batch_execution_files"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
