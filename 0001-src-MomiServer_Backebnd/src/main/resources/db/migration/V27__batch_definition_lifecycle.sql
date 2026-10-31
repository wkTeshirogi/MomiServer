ALTER TABLE m_batch_definitions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active',
    ADD CONSTRAINT chk_m_batch_definitions_version CHECK (version > 0),
    ADD CONSTRAINT chk_m_batch_definitions_status CHECK (status IN ('active', 'deleted')),
    ADD CONSTRAINT chk_m_batch_definitions_deleted_disabled CHECK (status <> 'deleted' OR enabled = false);

CREATE INDEX idx_m_batch_definitions_status
    ON m_batch_definitions (status);

COMMENT ON COLUMN m_batch_definitions.version IS '楽観的ロックに使用する更新番号。更新ごとに1加算する。';
COMMENT ON COLUMN m_batch_definitions.status IS '定義のライフサイクル状態。activeまたはdeleted。';
