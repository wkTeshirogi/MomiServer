CREATE TABLE IF NOT EXISTS m_api_versions (
    id BIGSERIAL PRIMARY KEY,
    api_group VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    deprecated_dt timestamp(3),
    retired_dt timestamp(3),
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_m_api_versions_group_version
        UNIQUE (api_group, version),
    CONSTRAINT chk_m_api_versions_group
        CHECK (api_group IN ('agent', 'iot', 'external')),
    CONSTRAINT chk_m_api_versions_version
        CHECK (version ~ '^v[0-9]+$'),
    CONSTRAINT chk_m_api_versions_status
        CHECK (status IN ('active', 'deprecated', 'retired'))
);

CREATE TABLE IF NOT EXISTS m_external_clients (
    id BIGSERIAL PRIMARY KEY,
    client_type VARCHAR(50) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    allowed_version VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_m_external_clients_type_name
        UNIQUE (client_type, client_name),
    CONSTRAINT chk_m_external_clients_type
        CHECK (client_type IN ('agent', 'iot')),
    CONSTRAINT chk_m_external_clients_allowed_version
        CHECK (allowed_version ~ '^v[0-9]+$'),
    CONSTRAINT chk_m_external_clients_status
        CHECK (status IN ('active', 'disabled', 'retired'))
);

CREATE INDEX IF NOT EXISTS idx_m_api_versions_group_status
    ON m_api_versions (api_group, status);

CREATE INDEX IF NOT EXISTS idx_m_external_clients_type_status
    ON m_external_clients (client_type, status);

CREATE OR REPLACE TRIGGER update_m_api_versions_updated_at
    BEFORE UPDATE ON "m_api_versions"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_m_external_clients_updated_at
    BEFORE UPDATE ON "m_external_clients"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

INSERT INTO m_api_versions (api_group, version, status)
VALUES
    ('agent', 'v1', 'active'),
    ('iot', 'v1', 'active')
ON CONFLICT (api_group, version) DO NOTHING;
