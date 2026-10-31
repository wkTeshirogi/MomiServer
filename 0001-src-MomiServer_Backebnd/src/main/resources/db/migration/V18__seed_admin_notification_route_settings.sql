WITH notification_routes(notification_type, source_type) AS (
    VALUES
        ('security_alert', 'security_event'),
        ('batch_completed', 'batch_execution'),
        ('batch_failed', 'batch_execution'),
        ('inventory_low', 'inventory'),
        ('system_notice', 'system')
),
admin_user AS (
    SELECT id
    FROM m_users
    WHERE email = 'admin@example.com'
)
UPDATE t_notification_route_settings route
SET
    minimum_level = 'info',
    browser_enabled = true,
    discord_enabled = true,
    enabled = true,
    updated_dt = CURRENT_TIMESTAMP
FROM notification_routes
CROSS JOIN admin_user
WHERE route.notification_type = notification_routes.notification_type
    AND route.source_type = notification_routes.source_type
    AND route.source_id IS NULL
    AND route.target_type = 'user'
    AND route.target_user_id = admin_user.id;

WITH notification_routes(notification_type, source_type) AS (
    VALUES
        ('security_alert', 'security_event'),
        ('batch_completed', 'batch_execution'),
        ('batch_failed', 'batch_execution'),
        ('inventory_low', 'inventory'),
        ('system_notice', 'system')
),
admin_user AS (
    SELECT id
    FROM m_users
    WHERE email = 'admin@example.com'
)
INSERT INTO t_notification_route_settings (
    notification_type,
    source_type,
    source_id,
    target_type,
    target_user_id,
    minimum_level,
    browser_enabled,
    discord_enabled,
    enabled
)
SELECT
    notification_routes.notification_type,
    notification_routes.source_type,
    NULL,
    'user',
    admin_user.id,
    'info',
    true,
    true,
    true
FROM notification_routes
CROSS JOIN admin_user
WHERE NOT EXISTS (
    SELECT 1
    FROM t_notification_route_settings route
    WHERE route.notification_type = notification_routes.notification_type
        AND route.source_type = notification_routes.source_type
        AND route.source_id IS NULL
        AND route.target_type = 'user'
        AND route.target_user_id = admin_user.id
);
