INSERT INTO m_notification_roles (
    role_code,
    role_name,
    description,
    enabled
)
VALUES
    ('ROLE_ALL', '全体通知', '通知ロール宛ての全通知を受信する。', true),
    ('ROLE_ITEM_STOCK', '在庫管理通知', '在庫注意および在庫切れに関する通知を受信する。', true)
ON CONFLICT (role_code) DO UPDATE
SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    enabled = true,
    updated_dt = CURRENT_TIMESTAMP;

WITH admin_user AS (
    SELECT id
    FROM m_users
    WHERE email = 'admin@example.com'
),
notification_roles AS (
    SELECT id
    FROM m_notification_roles
    WHERE role_code IN ('ROLE_ALL', 'ROLE_ITEM_STOCK')
)
INSERT INTO t_user_notification_roles (
    user_id,
    notification_role_id,
    enabled
)
SELECT
    admin_user.id,
    notification_roles.id,
    true
FROM admin_user
CROSS JOIN notification_roles
ON CONFLICT (user_id, notification_role_id) DO UPDATE
SET
    enabled = true,
    updated_dt = CURRENT_TIMESTAMP;
