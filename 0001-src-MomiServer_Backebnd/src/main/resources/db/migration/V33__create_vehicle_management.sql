CREATE TABLE IF NOT EXISTS t_vehicles (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    registration_number VARCHAR(50) NOT NULL,
    chassis_number VARCHAR(100) NOT NULL,
    vehicle_identification_code VARCHAR(100),
    vehicle_name VARCHAR(100) NOT NULL,
    model_code VARCHAR(100),
    first_registration_month VARCHAR(7),
    inspection_expiry_date DATE NOT NULL,
    issued_date DATE,
    usage_category VARCHAR(50),
    purpose VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'VALID',
    notes TEXT,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_t_vehicles_status
        CHECK (status IN ('VALID', 'DUE_SOON', 'URGENT', 'EXPIRED', 'UNKNOWN', 'ARCHIVED')),
    CONSTRAINT chk_t_vehicles_first_registration_month
        CHECK (first_registration_month IS NULL OR first_registration_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_vehicles_active_chassis_number
    ON t_vehicles (chassis_number)
    WHERE status <> 'ARCHIVED';

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_vehicles_active_identification_code
    ON t_vehicles (vehicle_identification_code)
    WHERE vehicle_identification_code IS NOT NULL AND status <> 'ARCHIVED';

CREATE INDEX IF NOT EXISTS idx_t_vehicles_expiry_date
    ON t_vehicles (inspection_expiry_date);

CREATE INDEX IF NOT EXISTS idx_t_vehicles_status
    ON t_vehicles (status);

CREATE TABLE IF NOT EXISTS t_vehicle_inspection_histories (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    previous_expiry_date DATE,
    new_expiry_date DATE,
    changed_by VARCHAR(255) NOT NULL,
    changed_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_t_vehicle_inspection_histories_vehicle
        FOREIGN KEY (vehicle_id)
            REFERENCES t_vehicles (id),
    CONSTRAINT chk_t_vehicle_inspection_histories_change_type
        CHECK (change_type IN ('CREATED', 'EXPIRY_UPDATED', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_t_vehicle_inspection_histories_vehicle_id
    ON t_vehicle_inspection_histories (vehicle_id, changed_dt DESC);

CREATE OR REPLACE TRIGGER update_t_vehicles_updated_at
    BEFORE UPDATE ON t_vehicles
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
