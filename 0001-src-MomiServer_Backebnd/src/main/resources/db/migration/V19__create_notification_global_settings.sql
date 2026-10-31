CREATE TABLE IF NOT EXISTS t_notification_global_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT true,
    browser_enabled BOOLEAN NOT NULL DEFAULT true,
    discord_enabled BOOLEAN NOT NULL DEFAULT false,
    discord_webhook_url TEXT,
    discord_minimum_level VARCHAR(20) NOT NULL DEFAULT 'warning',
    security_event_discord_enabled BOOLEAN NOT NULL DEFAULT false,
    security_event_minimum_level VARCHAR(20) NOT NULL DEFAULT 'warning',
    security_event_aggregation_window_seconds INTEGER NOT NULL DEFAULT 300,
    retention_days INTEGER NOT NULL DEFAULT 365,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_t_notification_global_settings_singleton
        CHECK (id = 1),
    CONSTRAINT chk_t_notification_global_settings_discord_minimum_level
        CHECK (discord_minimum_level IN ('info', 'warning', 'critical')),
    CONSTRAINT chk_t_notification_global_settings_security_event_minimum_level
        CHECK (security_event_minimum_level IN ('info', 'warning', 'critical')),
    CONSTRAINT chk_t_notification_global_settings_security_event_aggregation
        CHECK (security_event_aggregation_window_seconds > 0),
    CONSTRAINT chk_t_notification_global_settings_retention_days
        CHECK (retention_days > 0)
);

CREATE OR REPLACE TRIGGER update_t_notification_global_settings_updated_at
    BEFORE UPDATE ON "t_notification_global_settings"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE t_notification_global_settings IS '通知機能全体の有効化、通知チャネル、保持期間などの運用設定を管理する。';

COMMENT ON COLUMN t_notification_global_settings.enabled IS '通知配信全体の有効/無効。無効時も通知本体の記録は保持し、ユーザー配信と外部配信を抑止する。';

COMMENT ON COLUMN t_notification_global_settings.discord_webhook_url IS 'Discord Webhook URL。画面表示ではマスクし、未設定時はDiscord通知を送信しない。';
