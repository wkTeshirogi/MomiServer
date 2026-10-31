CREATE SEQUENCE IF NOT EXISTS m_batch_schedules_id_seq;

SELECT setval(
    'm_batch_schedules_id_seq',
    COALESCE((SELECT MAX(id) FROM m_batch_schedules), 0) + 1,
    false
);

ALTER TABLE m_batch_schedules
    ALTER COLUMN id SET DEFAULT nextval('m_batch_schedules_id_seq');

ALTER SEQUENCE m_batch_schedules_id_seq
    OWNED BY m_batch_schedules.id;
