ALTER TABLE t_notifications DROP CONSTRAINT chk_t_notifications_type;
ALTER TABLE t_notifications ADD CONSTRAINT chk_t_notifications_type
    CHECK (notification_type IN ('security_alert', 'batch_completed', 'batch_failed', 'inventory_low', 'system_notice', 'vehicle_inspection_expiry'));
ALTER TABLE t_notifications DROP CONSTRAINT chk_t_notifications_source_type;
ALTER TABLE t_notifications ADD CONSTRAINT chk_t_notifications_source_type
    CHECK (source_type IN ('security_event', 'batch_execution', 'inventory', 'system', 'vehicle'));

ALTER TABLE t_notification_route_settings DROP CONSTRAINT chk_t_notification_route_settings_type;
ALTER TABLE t_notification_route_settings ADD CONSTRAINT chk_t_notification_route_settings_type
    CHECK (notification_type IN ('security_alert', 'batch_completed', 'batch_failed', 'inventory_low', 'system_notice', 'vehicle_inspection_expiry'));
ALTER TABLE t_notification_route_settings DROP CONSTRAINT chk_t_notification_route_settings_source_type;
ALTER TABLE t_notification_route_settings ADD CONSTRAINT chk_t_notification_route_settings_source_type
    CHECK (source_type IN ('security_event', 'batch_execution', 'inventory', 'system', 'vehicle'));

ALTER TABLE t_user_notification_preferences DROP CONSTRAINT chk_t_user_notification_preferences_type;
ALTER TABLE t_user_notification_preferences ADD CONSTRAINT chk_t_user_notification_preferences_type
    CHECK (notification_type IN ('security_alert', 'batch_completed', 'batch_failed', 'inventory_low', 'system_notice', 'vehicle_inspection_expiry'));

CREATE TABLE t_vehicle_inspection_notifications (
    vehicle_id BIGINT NOT NULL REFERENCES t_vehicles(id) ON DELETE CASCADE,
    inspection_expiry_date DATE NOT NULL,
    notification_stage VARCHAR(30) NOT NULL,
    notification_id UUID REFERENCES t_notifications(notification_id) ON DELETE SET NULL,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vehicle_id, inspection_expiry_date, notification_stage),
    CONSTRAINT chk_t_vehicle_inspection_notifications_stage
        CHECK (notification_stage IN ('TWO_MONTHS', 'ONE_MONTH', 'SEVEN_DAYS', 'EXPIRED'))
);
COMMENT ON TABLE t_vehicle_inspection_notifications IS '車両・満了日・通知段階ごとの通知済み記録。通知本体削除後も重複防止のため保持する。';

INSERT INTO t_notification_route_settings (
    notification_type, source_type, source_id, target_type, target_role,
    minimum_level, browser_enabled, discord_enabled, enabled
)
SELECT 'vehicle_inspection_expiry', 'vehicle', NULL, 'notification_role', 'ROLE_ALL',
    'warning', true, false, true
WHERE NOT EXISTS (
    SELECT 1 FROM t_notification_route_settings
    WHERE notification_type = 'vehicle_inspection_expiry' AND source_type = 'vehicle'
      AND source_id IS NULL AND target_type = 'notification_role' AND target_role = 'ROLE_ALL'
);
