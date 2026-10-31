CREATE TABLE t_batch_global_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    setting_version BIGINT NOT NULL DEFAULT 1 CHECK (setting_version > 0),
    max_concurrent_executions INTEGER NOT NULL DEFAULT 3 CHECK (max_concurrent_executions >= 1),
    default_timeout_seconds INTEGER NOT NULL DEFAULT 1800 CHECK (default_timeout_seconds >= 1),
    result_retry_interval_seconds INTEGER NOT NULL DEFAULT 300 CHECK (result_retry_interval_seconds >= 1),
    result_retry_count INTEGER NOT NULL DEFAULT 5 CHECK (result_retry_count >= 0),
    unsent_result_retention_days INTEGER NOT NULL DEFAULT 7 CHECK (unsent_result_retention_days >= 1),
    result_wait_timeout_seconds INTEGER NOT NULL DEFAULT 3600 CHECK (result_wait_timeout_seconds >= 1),
    start_wait_timeout_seconds INTEGER NOT NULL DEFAULT 3600 CHECK (start_wait_timeout_seconds >= 1),
    cancel_grace_seconds INTEGER NOT NULL DEFAULT 30 CHECK (cancel_grace_seconds >= 1),
    created_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_dt timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_batch_global_settings_result_wait
        CHECK (result_wait_timeout_seconds > default_timeout_seconds)
);

INSERT INTO t_batch_global_settings (id) VALUES (1);

CREATE TRIGGER update_t_batch_global_settings_updated_at
    BEFORE UPDATE ON t_batch_global_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE t_batch_global_settings IS 'バッチ共通設定。エージェント実行制御およびサーバー結果確認の既定値。';
COMMENT ON COLUMN t_batch_global_settings.setting_version IS '保存ごとに増加する設定バージョン。';
COMMENT ON COLUMN t_batch_global_settings.max_concurrent_executions IS 'エージェント1台あたりの同時実行数';
COMMENT ON COLUMN t_batch_global_settings.default_timeout_seconds IS '標準実行タイムアウト（秒）';
COMMENT ON COLUMN t_batch_global_settings.result_retry_interval_seconds IS '結果再送間隔（秒）';
COMMENT ON COLUMN t_batch_global_settings.result_retry_count IS '結果再送回数（初回を除く）';
COMMENT ON COLUMN t_batch_global_settings.unsent_result_retention_days IS '未送信結果保持期間（日）';
COMMENT ON COLUMN t_batch_global_settings.result_wait_timeout_seconds IS '結果待ち期限（秒）';
COMMENT ON COLUMN t_batch_global_settings.start_wait_timeout_seconds IS '開始待ち期限（秒）';
COMMENT ON COLUMN t_batch_global_settings.cancel_grace_seconds IS 'キャンセル停止猶予（秒）';
