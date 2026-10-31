CREATE INDEX IF NOT EXISTS idx_batch_execution_requests_calendar
    ON t_batch_execution_requests (scheduled_fire_time, schedule_id, execution_status)
    WHERE schedule_id IS NOT NULL;
