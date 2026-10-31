INSERT INTO m_batch_definitions (
    batch_name,
    view_name,
    description,
    enabled,
    category,
    tags,
    execution_type,
    execution_command,
    working_directory,
    timeout_seconds,
    max_retry_count,
    retry_interval_seconds,
    execution_user,
    required_role,
    priority,
    allow_parallel_execution,
    log_retention_date,
    log_path,
    success_notification,
    failure_notification
) VALUES
    (
        'daily_log_cleanup',
        'Daily Log Cleanup',
        'Delete old application log files.',
        true,
        'maintenance',
        'log,cleanup,daily',
        'powershell',
        'powershell.exe -ExecutionPolicy Bypass -File scripts\cleanup-logs.ps1',
        'D:\6000.PROJECTS\MomiServer\batch',
        600,
        1,
        60,
        'batch_user',
        'ADMIN',
        3,
        false,
        DATE '2999-12-31',
        'logs/batch/daily_log_cleanup.log',
        'none',
        'discord'
    ),
    (
        'database_backup',
        'Database Backup',
        'Create a PostgreSQL backup file.',
        true,
        'backup',
        'db,backup',
        'command',
        'cmd.exe /c scripts\backup-db.bat',
        'D:\6000.PROJECTS\MomiServer\batch',
        1800,
        2,
        300,
        'batch_user',
        'ADMIN',
        1,
        false,
        DATE '2999-12-31',
        'logs/batch/database_backup.log',
        'discord',
        'discord'
    ),
    (
        'service_health_check',
        'Service Health Check',
        'Check registered service statuses.',
        true,
        'monitoring',
        'service,healthcheck',
        'java',
        'java -jar batch\service-health-check.jar',
        'D:\6000.PROJECTS\MomiServer',
        300,
        0,
        NULL,
        'batch_user',
        'ADMIN',
        2,
        true,
        DATE '2999-12-31',
        'logs/batch/service_health_check.log',
        'none',
        'discord'
    )
ON CONFLICT (batch_name) DO UPDATE SET
    view_name = EXCLUDED.view_name,
    description = EXCLUDED.description,
    enabled = EXCLUDED.enabled,
    category = EXCLUDED.category,
    tags = EXCLUDED.tags,
    execution_type = EXCLUDED.execution_type,
    execution_command = EXCLUDED.execution_command,
    working_directory = EXCLUDED.working_directory,
    timeout_seconds = EXCLUDED.timeout_seconds,
    max_retry_count = EXCLUDED.max_retry_count,
    retry_interval_seconds = EXCLUDED.retry_interval_seconds,
    execution_user = EXCLUDED.execution_user,
    required_role = EXCLUDED.required_role,
    priority = EXCLUDED.priority,
    allow_parallel_execution = EXCLUDED.allow_parallel_execution,
    log_retention_date = EXCLUDED.log_retention_date,
    log_path = EXCLUDED.log_path,
    success_notification = EXCLUDED.success_notification,
    failure_notification = EXCLUDED.failure_notification;

INSERT INTO m_batch_definition_parameters (
    batch_definition_id,
    parameter_name,
    parameter_type,
    fixed_value,
    dynamic_value_type,
    value_format,
    display_order,
    enabled
)
SELECT id, 'retention_days', 'fixed', '30', NULL, NULL, 1, true
FROM m_batch_definitions
WHERE batch_name = 'daily_log_cleanup'
ON CONFLICT (batch_definition_id, parameter_name) DO UPDATE SET
    parameter_type = EXCLUDED.parameter_type,
    fixed_value = EXCLUDED.fixed_value,
    dynamic_value_type = EXCLUDED.dynamic_value_type,
    value_format = EXCLUDED.value_format,
    display_order = EXCLUDED.display_order,
    enabled = EXCLUDED.enabled;

INSERT INTO m_batch_definition_parameters (
    batch_definition_id,
    parameter_name,
    parameter_type,
    fixed_value,
    dynamic_value_type,
    value_format,
    display_order,
    enabled
)
SELECT id, 'backup_date', 'dynamic_date', NULL, 'current_date', 'yyyyMMdd', 1, true
FROM m_batch_definitions
WHERE batch_name = 'database_backup'
ON CONFLICT (batch_definition_id, parameter_name) DO UPDATE SET
    parameter_type = EXCLUDED.parameter_type,
    fixed_value = EXCLUDED.fixed_value,
    dynamic_value_type = EXCLUDED.dynamic_value_type,
    value_format = EXCLUDED.value_format,
    display_order = EXCLUDED.display_order,
    enabled = EXCLUDED.enabled;
