CREATE TABLE IF NOT EXISTS m_notification_roles (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_m_notification_roles_role_code
        UNIQUE (role_code)
);

CREATE TABLE IF NOT EXISTS t_user_notification_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_role_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_user_notification_roles_user
        FOREIGN KEY (user_id)
            REFERENCES m_users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_t_user_notification_roles_role
        FOREIGN KEY (notification_role_id)
            REFERENCES m_notification_roles (id)
            ON DELETE CASCADE,
    CONSTRAINT uk_t_user_notification_roles_user_role
        UNIQUE (user_id, notification_role_id)
);

CREATE TABLE IF NOT EXISTS t_notification_route_settings (
    id BIGSERIAL PRIMARY KEY,
    notification_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(100),
    target_type VARCHAR(20) NOT NULL,
    target_role VARCHAR(50),
    target_user_id BIGINT,
    minimum_level VARCHAR(20) NOT NULL DEFAULT 'info',
    browser_enabled BOOLEAN NOT NULL DEFAULT true,
    discord_enabled BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_notification_route_settings_target_user
        FOREIGN KEY (target_user_id)
            REFERENCES m_users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_t_notification_route_settings_target_role
        FOREIGN KEY (target_role)
            REFERENCES m_notification_roles (role_code)
            ON DELETE RESTRICT,
    CONSTRAINT chk_t_notification_route_settings_type
        CHECK (notification_type IN ('security_alert', 'batch_completed', 'batch_failed', 'inventory_low', 'system_notice')),
    CONSTRAINT chk_t_notification_route_settings_source_type
        CHECK (source_type IN ('security_event', 'batch_execution', 'inventory', 'system')),
    CONSTRAINT chk_t_notification_route_settings_target_type
        CHECK (target_type IN ('notification_role', 'user')),
    CONSTRAINT chk_t_notification_route_settings_minimum_level
        CHECK (minimum_level IN ('info', 'warning', 'critical')),
    CONSTRAINT chk_t_notification_route_settings_target
        CHECK (
            (target_type = 'notification_role' AND target_role IS NOT NULL AND target_user_id IS NULL)
            OR (target_type = 'user' AND target_role IS NULL AND target_user_id IS NOT NULL)
        )
);

CREATE TABLE IF NOT EXISTS t_user_notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    minimum_level VARCHAR(20) NOT NULL DEFAULT 'info',
    browser_enabled BOOLEAN NOT NULL DEFAULT true,
    discord_enabled BOOLEAN NOT NULL DEFAULT true,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_user_notification_preferences_user
        FOREIGN KEY (user_id)
            REFERENCES m_users (id)
            ON DELETE CASCADE,
    CONSTRAINT uk_t_user_notification_preferences_user_type
        UNIQUE (user_id, notification_type),
    CONSTRAINT chk_t_user_notification_preferences_type
        CHECK (notification_type IN ('security_alert', 'batch_completed', 'batch_failed', 'inventory_low', 'system_notice')),
    CONSTRAINT chk_t_user_notification_preferences_minimum_level
        CHECK (minimum_level IN ('info', 'warning', 'critical'))
);

CREATE INDEX IF NOT EXISTS idx_m_notification_roles_code_enabled
    ON m_notification_roles (role_code, enabled);

CREATE INDEX IF NOT EXISTS idx_t_user_notification_roles_role
    ON t_user_notification_roles (notification_role_id, enabled);

CREATE INDEX IF NOT EXISTS idx_t_user_notification_roles_user
    ON t_user_notification_roles (user_id, enabled);

CREATE INDEX IF NOT EXISTS idx_t_notification_route_settings_lookup
    ON t_notification_route_settings (notification_type, source_type, source_id, enabled);

CREATE INDEX IF NOT EXISTS idx_t_notification_route_settings_role
    ON t_notification_route_settings (target_role)
    WHERE target_type = 'notification_role';

CREATE INDEX IF NOT EXISTS idx_t_notification_route_settings_user
    ON t_notification_route_settings (target_user_id)
    WHERE target_type = 'user';

CREATE INDEX IF NOT EXISTS idx_t_user_notification_preferences_user
    ON t_user_notification_preferences (user_id, notification_type, enabled);

CREATE OR REPLACE TRIGGER update_m_notification_roles_updated_at
    BEFORE UPDATE ON "m_notification_roles"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_t_user_notification_roles_updated_at
    BEFORE UPDATE ON "t_user_notification_roles"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_t_notification_route_settings_updated_at
    BEFORE UPDATE ON "t_notification_route_settings"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_t_user_notification_preferences_updated_at
    BEFORE UPDATE ON "t_user_notification_preferences"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE m_notification_roles IS '認可用ユーザー権限とは独立した通知配信用ロールを管理する。';

COMMENT ON TABLE t_user_notification_roles IS 'ユーザーと通知配信用ロールの割り当てを管理する。';

COMMENT ON TABLE t_notification_route_settings IS '通知種別と発生元ごとに、通知ロールまたは個別ユーザーへの通知ルートを管理する。';

COMMENT ON COLUMN t_notification_route_settings.source_id IS 'NULLの場合は、同一source_type内の全体設定として扱う。';

COMMENT ON COLUMN t_notification_route_settings.target_role IS 'm_users.roleではなく、m_notification_roles.role_codeを参照する通知専用ロールコード。';

COMMENT ON TABLE t_user_notification_preferences IS '通知対象に解決されたユーザーが、通知種別ごとに実際に受け取るかを管理する。';
