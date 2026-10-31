ALTER TABLE m_batch_definitions
    ADD COLUMN IF NOT EXISTS execution_command VARCHAR(1000) NOT NULL DEFAULT '';
