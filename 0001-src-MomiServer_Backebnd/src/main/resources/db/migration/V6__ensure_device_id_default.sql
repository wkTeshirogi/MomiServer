CREATE SEQUENCE IF NOT EXISTS m_devices_id_seq;

SELECT setval(
    'm_devices_id_seq',
    COALESCE((SELECT MAX(id) FROM m_devices), 0) + 1,
    false
);

ALTER TABLE m_devices
    ALTER COLUMN id SET DEFAULT nextval('m_devices_id_seq');

ALTER SEQUENCE m_devices_id_seq
    OWNED BY m_devices.id;
