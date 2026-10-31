ALTER TABLE m_users
    ADD COLUMN username VARCHAR(100),
    ADD COLUMN theme VARCHAR(10) NOT NULL DEFAULT 'dark';

ALTER TABLE m_users
    ADD CONSTRAINT chk_m_users_username CHECK (username IS NULL OR length(btrim(username)) > 0),
    ADD CONSTRAINT chk_m_users_theme CHECK (theme IN ('light', 'dark'));

COMMENT ON COLUMN m_users.username IS 'Personal display name; NULL for existing/unconfigured users';
COMMENT ON COLUMN m_users.theme IS 'Personal color theme: light or dark';
