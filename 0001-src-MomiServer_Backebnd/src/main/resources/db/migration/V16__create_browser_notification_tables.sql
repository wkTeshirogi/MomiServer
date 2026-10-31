CREATE TABLE IF NOT EXISTS t_notifications (
    id BIGSERIAL PRIMARY KEY,
    notification_id UUID NOT NULL DEFAULT gen_random_uuid(),
    notification_type VARCHAR(50) NOT NULL,
    level VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(100),
    link_path VARCHAR(500),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at timestamp(3),
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_t_notifications_notification_id
        UNIQUE (notification_id),
    CONSTRAINT chk_t_notifications_type
        CHECK (notification_type IN ('security_alert', 'batch_completed', 'batch_failed', 'inventory_low', 'system_notice')),
    CONSTRAINT chk_t_notifications_level
        CHECK (level IN ('info', 'warning', 'critical')),
    CONSTRAINT chk_t_notifications_source_type
        CHECK (source_type IN ('security_event', 'batch_execution', 'inventory', 'system')),
    CONSTRAINT chk_t_notifications_metadata_json_object
        CHECK (jsonb_typeof(metadata_json) = 'object')
);

CREATE TABLE IF NOT EXISTS t_user_notifications (
    id BIGSERIAL PRIMARY KEY,
    notification_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    delivery_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    read_status VARCHAR(30) NOT NULL DEFAULT 'unread',
    delivered_at timestamp(3),
    read_at timestamp(3),
    dismissed_at timestamp(3),
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_user_notifications_notification
        FOREIGN KEY (notification_id)
            REFERENCES t_notifications (notification_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_t_user_notifications_user
        FOREIGN KEY (user_id)
            REFERENCES m_users (id)
            ON DELETE CASCADE,
    CONSTRAINT uk_t_user_notifications_user_notification
        UNIQUE (user_id, notification_id),
    CONSTRAINT chk_t_user_notifications_delivery_status
        CHECK (delivery_status IN ('pending', 'delivered', 'failed')),
    CONSTRAINT chk_t_user_notifications_read_status
        CHECK (read_status IN ('unread', 'read', 'dismissed')),
    CONSTRAINT chk_t_user_notifications_delivered_at
        CHECK (delivery_status <> 'delivered' OR delivered_at IS NOT NULL),
    CONSTRAINT chk_t_user_notifications_read_at
        CHECK (read_status <> 'read' OR read_at IS NOT NULL),
    CONSTRAINT chk_t_user_notifications_dismissed_at
        CHECK (read_status <> 'dismissed' OR dismissed_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_t_notifications_type_created
    ON t_notifications (notification_type, created_dt);

CREATE INDEX IF NOT EXISTS idx_t_notifications_level_created
    ON t_notifications (level, created_dt);

CREATE INDEX IF NOT EXISTS idx_t_notifications_source
    ON t_notifications (source_type, source_id);

CREATE INDEX IF NOT EXISTS idx_t_notifications_expires_at
    ON t_notifications (expires_at);

CREATE INDEX IF NOT EXISTS idx_t_notifications_created_dt
    ON t_notifications (created_dt);

CREATE INDEX IF NOT EXISTS idx_t_user_notifications_user_read
    ON t_user_notifications (user_id, read_status, created_dt);

CREATE INDEX IF NOT EXISTS idx_t_user_notifications_user_delivery
    ON t_user_notifications (user_id, delivery_status, created_dt);

CREATE INDEX IF NOT EXISTS idx_t_user_notifications_notification_id
    ON t_user_notifications (notification_id);

CREATE OR REPLACE TRIGGER update_t_notifications_updated_at
    BEFORE UPDATE ON "t_notifications"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_t_user_notifications_updated_at
    BEFORE UPDATE ON "t_user_notifications"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
