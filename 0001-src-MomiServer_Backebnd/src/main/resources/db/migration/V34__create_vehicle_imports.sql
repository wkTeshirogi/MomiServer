CREATE TABLE t_vehicle_imports (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    detected_media_type VARCHAR(64) NOT NULL,
    storage_reference VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    display_name VARCHAR(100),
    registration_number VARCHAR(50),
    chassis_number VARCHAR(100),
    vehicle_name VARCHAR(100),
    model_code VARCHAR(100),
    first_registration_month VARCHAR(7),
    inspection_expiry_date DATE,
    issued_date DATE,
    extraction_method VARCHAR(32),
    warnings TEXT,
    expires_at TIMESTAMP(3) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_dt TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_vehicle_id BIGINT,
    CONSTRAINT chk_t_vehicle_imports_status CHECK (status IN
        ('PENDING_REVIEW', 'REGISTERED', 'CANCELLED', 'PARSE_FAILED', 'EXPIRED')),
    CONSTRAINT fk_t_vehicle_imports_confirmed_vehicle FOREIGN KEY (confirmed_vehicle_id)
        REFERENCES t_vehicles (id),
    CONSTRAINT chk_t_vehicle_imports_first_registration_month CHECK
        (first_registration_month IS NULL OR first_registration_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);

CREATE INDEX idx_t_vehicle_imports_status_expiry ON t_vehicle_imports (status, expires_at);

CREATE OR REPLACE TRIGGER update_t_vehicle_imports_updated_at
    BEFORE UPDATE ON t_vehicle_imports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
