ALTER TABLE t_batch_execution_requests
    DROP CONSTRAINT chk_t_batch_execution_requests_status,
    ADD CONSTRAINT chk_t_batch_execution_requests_status
        CHECK (execution_status IN ('queued', 'dispatched', 'running', 'success', 'failed', 'timeout', 'cancelled', 'expired')),
    ADD COLUMN setting_version BIGINT,
    ADD COLUMN max_concurrent_executions INTEGER NOT NULL DEFAULT 3 CHECK (max_concurrent_executions > 0),
    ADD COLUMN result_retry_interval_seconds INTEGER NOT NULL DEFAULT 300 CHECK (result_retry_interval_seconds > 0),
    ADD COLUMN result_retry_count INTEGER NOT NULL DEFAULT 5 CHECK (result_retry_count >= 0),
    ADD COLUMN unsent_result_retention_days INTEGER NOT NULL DEFAULT 7 CHECK (unsent_result_retention_days > 0),
    ADD COLUMN result_wait_timeout_seconds BIGINT NOT NULL DEFAULT 3600 CHECK (result_wait_timeout_seconds > 0),
    ADD COLUMN cancel_grace_seconds INTEGER NOT NULL DEFAULT 30 CHECK (cancel_grace_seconds > 0),
    ADD COLUMN start_deadline_at timestamp(3),
    ADD COLUMN start_received_at timestamp(3),
    ADD COLUMN result_deadline_at timestamp(3),
    ADD COLUMN cancel_requested_at timestamp(3),
    ADD COLUMN cancel_requested_by VARCHAR(255),
    ADD COLUMN last_cancel_attempt_at timestamp(3),
    ADD COLUMN confirmation_status VARCHAR(30) NOT NULL DEFAULT 'pending'
        CHECK (confirmation_status IN ('pending', 'start_unconfirmed', 'result_unconfirmed', 'stop_unconfirmed', 'confirmed'));

-- Existing requests retain their explicit timeout. No historical terminal result is rewritten.
UPDATE t_batch_execution_requests r
SET setting_version = s.setting_version,
    max_concurrent_executions = s.max_concurrent_executions,
    result_retry_interval_seconds = s.result_retry_interval_seconds,
    result_retry_count = s.result_retry_count,
    unsent_result_retention_days = s.unsent_result_retention_days,
    result_wait_timeout_seconds = GREATEST(s.result_wait_timeout_seconds::BIGINT, r.timeout_seconds::BIGINT + 1),
    cancel_grace_seconds = s.cancel_grace_seconds,
    start_deadline_at = r.requested_at + s.start_wait_timeout_seconds * INTERVAL '1 second',
    start_received_at = r.started_at,
    result_deadline_at = r.started_at
        + GREATEST(s.result_wait_timeout_seconds::BIGINT, r.timeout_seconds::BIGINT + 1) * INTERVAL '1 second',
    confirmation_status = CASE WHEN r.execution_status IN ('queued', 'dispatched', 'running')
        THEN 'pending' ELSE 'confirmed' END
FROM t_batch_global_settings s WHERE s.id = 1;

ALTER TABLE t_batch_execution_requests ALTER COLUMN start_deadline_at SET NOT NULL;
CREATE INDEX idx_batch_execution_control_active ON t_batch_execution_requests (execution_status, start_deadline_at)
    WHERE execution_status IN ('queued', 'dispatched', 'running');

COMMENT ON COLUMN t_batch_execution_requests.confirmation_status IS '実行状態と別管理の確認状況。期限超過だけで実行完了とはしない。';
COMMENT ON COLUMN t_batch_execution_requests.start_received_at IS '最初の開始通知をサーバーが受理した日時。再送で延長しない。';
COMMENT ON COLUMN t_batch_execution_requests.cancel_requested_at IS 'キャンセル要求日時。停止確認とは区別する。';
