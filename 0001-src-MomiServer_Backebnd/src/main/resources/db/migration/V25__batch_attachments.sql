ALTER TABLE t_batch_global_settings
    ADD COLUMN attachment_max_file_mib INTEGER CHECK (attachment_max_file_mib BETWEEN 1 AND 10240),
    ADD COLUMN attachment_max_total_mib INTEGER CHECK (attachment_max_total_mib BETWEEN 1 AND 102400),
    ADD COLUMN attachment_max_count INTEGER CHECK (attachment_max_count BETWEEN 1 AND 1000),
    ADD COLUMN work_directory_retention_days INTEGER CHECK (work_directory_retention_days BETWEEN 1 AND 3650);

CREATE TABLE m_batch_files (
    id BIGSERIAL PRIMARY KEY,
    batch_definition_id BIGINT NOT NULL REFERENCES m_batch_definitions(id),
    upload_request_id UUID NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    destination_path VARCHAR(500) NOT NULL,
    file_name_key VARCHAR(500) NOT NULL,
    file_category VARCHAR(20) NOT NULL CHECK (file_category IN ('script','input','config')),
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL CHECK (file_size >= 0),
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active','deleted')),
    replaces_id BIGINT REFERENCES m_batch_files(id),
    created_dt TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_m_batch_files_active_name ON m_batch_files(batch_definition_id,file_name_key)
    WHERE status='active';
ALTER TABLE t_batch_execution_requests
    ADD COLUMN attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN work_directory_retention_days INTEGER CHECK (work_directory_retention_days BETWEEN 1 AND 3650);
