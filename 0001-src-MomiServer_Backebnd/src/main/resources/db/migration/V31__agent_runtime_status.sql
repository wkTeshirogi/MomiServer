CREATE TABLE t_agent_machine_status (
    agent_id UUID PRIMARY KEY,
    generation BIGINT NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    cpu_percent NUMERIC(5, 2) NOT NULL,
    memory_percent NUMERIC(5, 2) NOT NULL,
    cpu_temperature_c NUMERIC(5, 2),
    gpu_temperature_c NUMERIC(5, 2),
    uptime_seconds BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_t_agent_machine_status_agent
        FOREIGN KEY (agent_id) REFERENCES m_agents(id) ON DELETE CASCADE,
    CONSTRAINT chk_t_agent_machine_status_cpu
        CHECK (cpu_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_t_agent_machine_status_memory
        CHECK (memory_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_t_agent_machine_status_uptime
        CHECK (uptime_seconds >= 0)
);

CREATE TABLE t_agent_storage_status (
    agent_id UUID NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    total_bytes BIGINT NOT NULL,
    used_bytes BIGINT NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (agent_id, storage_key),
    CONSTRAINT fk_t_agent_storage_status_agent
        FOREIGN KEY (agent_id) REFERENCES m_agents(id) ON DELETE CASCADE,
    CONSTRAINT chk_t_agent_storage_status_bytes
        CHECK (total_bytes >= 0 AND used_bytes >= 0 AND used_bytes <= total_bytes)
);

CREATE TABLE t_agent_service_status (
    service_id BIGINT PRIMARY KEY,
    agent_id UUID NOT NULL,
    generation BIGINT NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    process_id BIGINT,
    uptime_seconds BIGINT NOT NULL,
    cpu_percent NUMERIC(5, 2) NOT NULL,
    memory_percent NUMERIC(5, 2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_t_agent_service_status_service
        FOREIGN KEY (service_id) REFERENCES m_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_t_agent_service_status_agent
        FOREIGN KEY (agent_id) REFERENCES m_agents(id) ON DELETE CASCADE,
    CONSTRAINT chk_t_agent_service_status_value
        CHECK (status IN ('online', 'offline', 'unknown')),
    CONSTRAINT chk_t_agent_service_status_cpu
        CHECK (cpu_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_t_agent_service_status_memory
        CHECK (memory_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_t_agent_service_status_uptime
        CHECK (uptime_seconds >= 0)
);

CREATE INDEX idx_t_agent_machine_status_collected
    ON t_agent_machine_status(collected_at);
CREATE INDEX idx_t_agent_storage_status_collected
    ON t_agent_storage_status(collected_at);
CREATE INDEX idx_t_agent_service_status_agent_collected
    ON t_agent_service_status(agent_id, collected_at);
