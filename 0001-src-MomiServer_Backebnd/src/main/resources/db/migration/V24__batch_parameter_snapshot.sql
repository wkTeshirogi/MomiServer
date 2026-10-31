ALTER TABLE t_batch_execution_requests ADD COLUMN parameters JSONB NOT NULL DEFAULT '{}'::jsonb;
