# t_batch_execution_requests

## 概要

バッチ定義から生成された実行要求を管理するテーブル。
手動実行とスケジュール実行の両方を扱い、対象 Agent、実行コマンド、状態、dispatch 試行、開始・終了時刻を保持する。

## カラム定義

V23・V24追加項目:

| カラム | 型 | NULL | デフォルト | 説明 |
| --- | --- | --- | --- | --- |
| stdout / stderr | TEXT | 可 | なし | Agentの上限付き標準出力・標準エラー |
| output_truncated | BOOLEAN | 不可 | false | 収集上限による切り捨て |
| parameters | JSONB | 不可 | {} | 要求作成時に確定したパラメータ値。後から定義が変更されても上書きしない |

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | DB 内部 ID |
| 2 | batch_definition_id | BIGINT | 不可 | なし | 外部キー | 実行対象のバッチ定義 ID |
| 3 | schedule_id | BIGINT | 可 | なし | 外部キー | スケジュール実行時のスケジュール ID |
| 4 | target_agent_id | BIGINT | 不可 | なし | なし | 実行対象 Agent ID |
| 5 | requested_type | VARCHAR(50) | 不可 | なし | CHECK | `manual` または `schedule` |
| 6 | requested_by | VARCHAR(255) | 可 | なし | なし | 手動実行要求者。スケジュール実行時は `scheduler` |
| 7 | execution_status | VARCHAR(50) | 不可 | なし | CHECK | `queued`、`dispatched`、`running`、`success`、`failed`、`timeout`、`cancelled` |
| 8 | test_execution | BOOLEAN | 不可 | false | なし | テスト実行フラグ |
| 9 | execution_command | VARCHAR(1000) | 不可 | なし | なし | Agent が実行するコマンド |
| 10 | working_directory | VARCHAR(500) | 可 | なし | なし | 実行時の作業ディレクトリ |
| 11 | timeout_seconds | INTEGER | 不可 | 0 | なし | timeout 判定秒数。0 の場合は timeout 判定対象外 |
| 12 | max_retry_count | INTEGER | 不可 | 0 | なし | 最大 retry 回数 |
| 13 | retry_count | INTEGER | 不可 | 0 | CHECK | 現在の retry 回数 |
| 14 | log_path | VARCHAR(500) | 可 | なし | なし | 実行ログ出力先 |
| 15 | exit_code | INTEGER | 可 | なし | なし | Agent 実行プロセスの終了コード |
| 16 | message | TEXT | 可 | なし | なし | 実行結果メッセージ |
| 17 | requested_at | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 実行要求作成日時 |
| 18 | dispatched_at | timestamp(3) | 可 | なし | なし | MQTT publish 成功日時 |
| 19 | started_at | timestamp(3) | 可 | なし | なし | Agent 開始通知日時 |
| 20 | finished_at | timestamp(3) | 可 | なし | なし | Agent 完了通知または timeout 確定日時 |
| 21 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 22 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |
| 23 | execution_id | UUID | 不可 | gen_random_uuid() | 一意 | Agent 連携用 UUID |
| 24 | scheduled_fire_time | timestamp(3) | 可 | なし | なし | スケジュール実行予定時刻 |
| 25 | dispatch_attempt_count | INTEGER | 不可 | 0 | なし | MQTT publish 試行回数 |
| 26 | last_dispatch_attempt_at | timestamp(3) | 可 | なし | なし | 最終 dispatch 試行日時 |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_batch_execution_requests_agent_status | target_agent_id, execution_status, id | Agent・状態別の実行要求検索 |
| 2 | idx_t_batch_execution_requests_definition_id | batch_definition_id | バッチ定義別の実行要求検索 |
| 3 | idx_t_batch_execution_requests_status | execution_status | 状態別の dispatch / timeout 対象検索 |
| 4 | uk_t_batch_execution_requests_execution_id | execution_id | Agent 連携用 UUID の一意制約 |
| 5 | uk_t_batch_execution_requests_schedule_fire | schedule_id, scheduled_fire_time | スケジュール実行要求の重複作成防止 |

## 状態遷移

| 現在状態 | 契機 | 次状態 |
| --- | --- | --- |
| queued | MQTT publish 成功 | dispatched |
| queued | Agent 開始通知 | running |
| dispatched | Agent 開始通知 | running |
| running | Agent 正常完了通知 | success |
| running | Agent 異常完了通知 | failed |
| running | timeout 監視 | timeout |
| running | Agent cancel 通知 | cancelled |

## 関連処理

- 手動実行要求: `BatchExecutionRequestService#createManualRequest`
- スケジュール実行要求: `BatchExecutionRequestService#createScheduledRequest`
- 再 dispatch: `BatchExecutionScheduler#redispatchQueuedRequests`
- timeout 判定: `BatchExecutionScheduler#timeoutRunningRequests`
- 実行履歴保存: `BatchExecutionRequestService#saveHistory`


## Agent登録世代（V22追加）

| カラム | 型 | NULL | 説明 |
| --- | --- | --- | --- |
| target_agent_generation | BIGINT | 可 | 実行要求作成時に対象Agentがregisteredなら、その登録世代を保存する |

既存のtarget_agent_idは端末マスタの数値IDを使用する。Agentの外部UUIDとは別。
世代がNULLの旧要求・未登録時の要求を新端末へ自動付与しない。端末IDと世代を合わせて認可する。再登録で実行要求の世代を一括更新しない。


## V25: 添付と作業領域保持

V26ではカレンダー集計用に `idx_batch_execution_requests_calendar` を追加する。
キー順はscheduled_fire_time、schedule_id、execution_status。schedule_idがNULLでない行だけを対象にする。
列追加や既存状態の変更は行わない。

| カラム | 型 | NULL | 既定値・制約 | 内容 |
| --- | --- | --- | --- | --- |
| attachments | JSONB | 不可 | [] | 添付ID、元名、配置先、種別、サイズ、ハッシュ、登録日時の作成時スナップショット |
| work_directory_retention_days | INTEGER | 可 | NULL、1～3650 | 作成時の保持日数。NULLは添付実装前の旧要求 |

旧要求は元の作業ディレクトリ契約を維持する。新要求は添付の有無によらず実行専用ディレクトリを使用する。保持日数は作成後に変更しない。
