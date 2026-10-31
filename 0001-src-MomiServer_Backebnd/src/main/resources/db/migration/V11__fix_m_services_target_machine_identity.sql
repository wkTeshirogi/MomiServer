ALTER TABLE m_services
    ALTER COLUMN target_machine DROP DEFAULT;

DROP SEQUENCE IF EXISTS m_services_target_machine_seq;
