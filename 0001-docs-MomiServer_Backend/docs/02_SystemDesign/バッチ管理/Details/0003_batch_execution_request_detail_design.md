# バッチ実行要求 詳細設計

## 目的

手動実行またはスケジュール実行により、対象Agentへ送信するバッチ実行要求を作成し、状態を管理する。

## 作成元

| 作成元 | requested_type | 内容 |
| --- | --- | --- |
| 手動実行 | manual | 管理者操作により即時実行要求を作成する。 |
| スケジュール実行 | schedule | Cron式に一致したスケジュールから実行要求を作成する。 |

## 実行要求項目

| 項目 | 内容 |
| --- | --- |
| id | DB内部用ID。 |
| execution_id | Agent連携用UUID。 |
| batch_definition_id | バッチ定義ID。 |
| schedule_id | スケジュールID。 |
| target_agent_id | 対象Agent ID。 |
| requested_type | manualまたはschedule。 |
| requested_by | 手動実行時の要求者。 |
| execution_status | 実行状態。 |
| test_execution | テスト実行可否。 |
| execution_command | 実行コマンド。 |
| working_directory | 作業ディレクトリ。 |
| timeout_seconds | タイムアウト秒数。 |
| max_retry_count | 最大リトライ回数。 |
| retry_count | 現在のリトライ回数。 |
| dispatch_attempt_count | MQTT通知試行回数。 |
| log_path | ログ出力先。 |
| scheduled_fire_time | スケジュール実行予定時刻。 |

## 作成処理

```text
1. バッチ定義を取得する
2. 対象Agentを決定する
3. 実行要求IDを採番する
4. バッチ定義の実行時項目を実行要求へコピーする
5. 実行状態をqueuedで保存する
6. MQTT通知を試行する
7. 通知成功時はdispatchedへ更新する
```

## 状態遷移

| 現在状態 | 契機 | 次状態 |
| --- | --- | --- |
| queued | MQTT通知成功 | dispatched |
| dispatched | Agent開始通知 | running |
| queued | Agent開始通知 | running |
| running | Agent正常終了 | success |
| running | Agent異常終了 | failed |
| running | タイムアウト | timeout |
| running | キャンセル完了 | cancelled |

## 重複防止

| 対象 | 防止方法 |
| --- | --- |
| 手動実行 | 同一操作の二重送信は画面およびAPIで抑制する。 |
| スケジュール実行 | `schedule_id` と `scheduled_fire_time` の組み合わせで重複作成を防止する。 |
| Agent実行 | `execution_id` をAgent側の重複実行防止キーとする。 |

## タイムアウト管理

`running` の実行要求について、開始時刻からタイムアウト秒数を超過した場合は `timeout` に更新し、実行履歴を保存する。

## 再通知管理

`queued` の実行要求は再通知対象とする。MQTT通知に成功した場合は `dispatched` に更新する。

