-- 設定値ではなく実行中の認証状態だけを永続化する。既存ロールは変更しない。
ALTER TABLE m_users
    ADD COLUMN login_failure_count integer NOT NULL DEFAULT 0,
    ADD COLUMN login_locked_until timestamp(3),
    ADD CONSTRAINT chk_m_users_login_failure_count CHECK (login_failure_count >= 0);

ALTER TABLE t_vehicle_inspection_notifications DROP CONSTRAINT chk_t_vehicle_inspection_notifications_stage;
ALTER TABLE t_vehicle_inspection_notifications ADD CONSTRAINT chk_t_vehicle_inspection_notifications_stage
    CHECK (notification_stage IN ('TWO_MONTHS', 'ONE_MONTH', 'SEVEN_DAYS', 'DUE_TODAY', 'EXPIRED'));
