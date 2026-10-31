CREATE TABLE IF NOT EXISTS m_users (
                                       id BIGSERIAL PRIMARY KEY,
                                       email VARCHAR(255) UNIQUE NOT NULL,
                                       password VARCHAR(255) NOT NULL,
                                       role VARCHAR(50) NOT NULL,
                                       created_dt timestamp(3) not null default CURRENT_TIMESTAMP ,
                                       updated_dt timestamp(3) not null default CURRENT_TIMESTAMP ,
                                       lastLoginAt timestamp(3)
);

CREATE TABLE IF NOT EXISTS m_services(
                                         id BIGSERIAL PRIMARY KEY,
                                         service_name VARCHAR(255) NOT NULL,
                                         view_name VARCHAR(255) NOT NULL,
                                         target_machine BIGSERIAL NOT NULL ,
                                         status VARCHAR(50) NOT NULL,
                                         created_dt timestamp(3) not null default CURRENT_TIMESTAMP ,
                                         updated_dt timestamp(3) not null default CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS m_devices(
                                        id BIGSERIAL PRIMARY KEY
    , device_name VARCHAR (255) NOT NULL
    , address VARCHAR (15) NOT NULL
    , location VARCHAR (50)
    , mqtt_client_id VARCHAR (50) default ''
    , note VARCHAR (500) default ''
    , status VARCHAR (50) NOT NULL
    , created_dt timestamp (3) not null default CURRENT_TIMESTAMP
    , updated_dt timestamp (3) not null default CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS m_batch_definitions (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   batch_name VARCHAR(255) UNIQUE NOT NULL,
                                                   view_name VARCHAR(255) NOT NULL,
                                                   description TEXT,
                                                   enabled BOOLEAN NOT NULL DEFAULT true,
                                                   category VARCHAR(50) NOT NULL,
                                                   tags VARCHAR(500),
                                                   execution_type VARCHAR(50) NOT NULL,
                                                   execution_command VARCHAR(1000) NOT NULL DEFAULT '',
                                                   working_directory VARCHAR(500),
                                                   timeout_seconds INTEGER NOT NULL DEFAULT 0,
                                                   max_retry_count INTEGER NOT NULL DEFAULT 0,
                                                   retry_interval_seconds INTEGER,
                                                   execution_user VARCHAR(255),
                                                   required_role VARCHAR(50),
                                                   priority INTEGER NOT NULL DEFAULT 5,
                                                   allow_parallel_execution BOOLEAN NOT NULL DEFAULT false,
                                                   log_retention_date DATE NOT NULL DEFAULT DATE '2999-12-31',
                                                   log_path VARCHAR(500) NOT NULL,
                                                   success_notification VARCHAR(50) NOT NULL DEFAULT 'none',
                                                   failure_notification VARCHAR(50) NOT NULL DEFAULT 'none',
                                                   created_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                   updated_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                   CONSTRAINT chk_m_batch_definitions_timeout
                                                       CHECK (timeout_seconds >= 0),
                                                   CONSTRAINT chk_m_batch_definitions_max_retry
                                                       CHECK (max_retry_count BETWEEN 0 AND 9),
                                                   CONSTRAINT chk_m_batch_definitions_retry_interval
                                                       CHECK (retry_interval_seconds IS NULL OR retry_interval_seconds >= 0),
                                                   CONSTRAINT chk_m_batch_definitions_priority
                                                       CHECK (priority BETWEEN 1 AND 9)
);

CREATE TABLE IF NOT EXISTS m_batch_schedules (
                                                id BIGSERIAL PRIMARY KEY,
                                                batch_definition_id BIGINT NOT NULL,
                                                target_agent_id BIGINT NOT NULL,
                                                cron_expression VARCHAR(255) NOT NULL,
                                                start_date DATE,
                                                end_date DATE,
                                                allow_holiday_execution BOOLEAN NOT NULL DEFAULT false,
                                                enabled BOOLEAN NOT NULL DEFAULT true,
                                                priority INTEGER NOT NULL DEFAULT 5,
                                                created_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                updated_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                CONSTRAINT fk_m_batch_schedules_definition
                                                    FOREIGN KEY (batch_definition_id)
                                                        REFERENCES m_batch_definitions (id),
                                                CONSTRAINT chk_m_batch_schedules_period
                                                    CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
                                                CONSTRAINT chk_m_batch_schedules_priority
                                                    CHECK (priority BETWEEN 1 AND 9)
);

CREATE TABLE IF NOT EXISTS m_batch_definition_parameters (
                                                            id BIGSERIAL PRIMARY KEY,
                                                            batch_definition_id BIGINT NOT NULL,
                                                            parameter_name VARCHAR(255) NOT NULL,
                                                            parameter_type VARCHAR(50) NOT NULL,
                                                            fixed_value TEXT,
                                                            dynamic_value_type VARCHAR(50),
                                                            value_format VARCHAR(100),
                                                            display_order INTEGER NOT NULL,
                                                            enabled BOOLEAN NOT NULL DEFAULT true,
                                                            created_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                            updated_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                            CONSTRAINT fk_m_batch_definition_parameters_definition
                                                                FOREIGN KEY (batch_definition_id)
                                                                    REFERENCES m_batch_definitions (id),
                                                            CONSTRAINT uk_m_batch_definition_parameters_name
                                                                UNIQUE (batch_definition_id, parameter_name),
                                                            CONSTRAINT chk_m_batch_definition_parameters_display_order
                                                                CHECK (display_order >= 0),
                                                            CONSTRAINT chk_m_batch_definition_parameters_fixed_value
                                                                CHECK (parameter_type <> 'fixed' OR fixed_value IS NOT NULL),
                                                            CONSTRAINT chk_m_batch_definition_parameters_dynamic_value_type
                                                                CHECK (parameter_type NOT IN ('dynamic_date', 'dynamic_time')
                                                                    OR dynamic_value_type IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS t_batch_execution_histories (
                                                          id BIGSERIAL PRIMARY KEY,
                                                          batch_definition_id BIGINT NOT NULL,
                                                          test_execution BOOLEAN NOT NULL DEFAULT false,
                                                          execution_started_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                          execution_finished_dt timestamp(3),
                                                          execution_result VARCHAR(50) NOT NULL,
                                                          exit_code INTEGER,
                                                          log_path VARCHAR(500),
                                                          message TEXT,
                                                          created_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                          updated_dt timestamp(3) not null default CURRENT_TIMESTAMP,
                                                          CONSTRAINT fk_t_batch_execution_histories_definition
                                                              FOREIGN KEY (batch_definition_id)
                                                                  REFERENCES m_batch_definitions (id)
);

CREATE INDEX IF NOT EXISTS idx_m_batch_definitions_enabled
    ON m_batch_definitions (enabled);

CREATE INDEX IF NOT EXISTS idx_m_batch_definitions_category
    ON m_batch_definitions (category);

CREATE INDEX IF NOT EXISTS idx_m_batch_definitions_execution_type
    ON m_batch_definitions (execution_type);

CREATE INDEX IF NOT EXISTS idx_m_batch_definitions_priority
    ON m_batch_definitions (priority);

CREATE INDEX IF NOT EXISTS idx_m_batch_definitions_updated_dt
    ON m_batch_definitions (updated_dt);

CREATE INDEX IF NOT EXISTS idx_m_batch_schedules_definition_id
    ON m_batch_schedules (batch_definition_id);

CREATE INDEX IF NOT EXISTS idx_m_batch_schedules_target_agent_id
    ON m_batch_schedules (target_agent_id);

CREATE INDEX IF NOT EXISTS idx_m_batch_schedules_enabled
    ON m_batch_schedules (enabled);

CREATE INDEX IF NOT EXISTS idx_m_batch_definition_parameters_definition_id
    ON m_batch_definition_parameters (batch_definition_id);

CREATE INDEX IF NOT EXISTS idx_t_batch_execution_histories_definition_id
    ON t_batch_execution_histories (batch_definition_id);

CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS '
    BEGIN
        NEW."updated_dt" = CURRENT_TIMESTAMP;
        RETURN NEW;
    END;
' LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_m_users_updated_at
    BEFORE UPDATE ON "m_users"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_m_services_updated_at
    BEFORE UPDATE ON "m_services"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE
    OR REPLACE TRIGGER update_m_devices_updated_at BEFORE UPDATE
    ON "m_devices" FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_m_batch_definitions_updated_at
    BEFORE UPDATE ON "m_batch_definitions"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_m_batch_schedules_updated_at
    BEFORE UPDATE ON "m_batch_schedules"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_m_batch_definition_parameters_updated_at
    BEFORE UPDATE ON "m_batch_definition_parameters"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_t_batch_execution_histories_updated_at
    BEFORE UPDATE ON "t_batch_execution_histories"
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
