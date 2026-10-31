# バッチAgent連携 詳細設計

## 目的

バックエンドで作成したバッチ実行要求を対象Agentへ通知し、Agentから実行開始・実行完了の通知を受け付ける。

## MQTT通知

### Topic

```text
smarthome/agents/{agentId}/batch/command
```

`{agentId}` は対象Agent IDに置換する。

### QoS

QoSは `1` を基本とする。

Agent側ではQoS 1の重複配信に備え、`execution_id` 単位で二重実行を防止する。

### Payload

| 項目 | 内容 |
| --- | --- |
| id | DB内部用ID。 |
| execution_id | Agent連携用UUID。 |
| batch_definition_id | バッチ定義ID。 |
| schedule_id | スケジュールID。 |
| target_agent_id | 対象Agent ID。 |
| requested_type | manualまたはschedule。 |
| execution_status | 実行状態。 |
| test_execution | テスト実行可否。 |
| execution_command | 実行コマンド。 |
| working_directory | 作業ディレクトリ。 |
| timeout_seconds | タイムアウト秒数。 |
| max_retry_count | 最大リトライ回数。 |
| retry_count | リトライ回数。 |
| dispatch_attempt_count | MQTT通知試行回数。 |
| log_path | ログ出力先。 |
| scheduled_fire_time | スケジュール実行予定時刻。 |
| requested_at | 実行要求日時。 |

## Agent開始通知

```http
POST /api/v1/agent/batch/executions/{executionId}/start
```

### 開始通知処理

| 項目 | 内容 |
| --- | --- |
| 対象取得 | `execution_id` で実行要求を取得する。 |
| 状態更新 | 実行状態を `running` に更新する。 |
| 開始日時 | 開始日時を記録する。 |

## Agent完了通知

```http
POST /api/v1/agent/batch/executions/{executionId}/complete
```

### 入力

| 項目 | 内容 |
| --- | --- |
| execution_status | success、failed、timeout、cancelledのいずれか。 |
| exit_code | 実行プロセスの終了コード。 |
| log_path | ログ参照先。 |
| message | 実行結果メッセージ。 |

### 完了通知処理

| 項目 | 内容 |
| --- | --- |
| 対象取得 | `execution_id` で実行要求を取得する。 |
| 状態更新 | 完了通知の状態へ更新する。 |
| 終了日時 | 終了日時を記録する。 |
| 履歴保存 | 実行履歴を保存する。 |

## ファイル配布

ファイル本体はMQTTで送信せず、HTTPで配布する。

### ファイル一覧

```http
GET /api/v1/agent/batch/executions/{executionId}/files
```

### ファイルダウンロード

```http
GET /api/v1/agent/batch/executions/{executionId}/files/{fileId}/download
```

Agentはダウンロード後、必要に応じてSHA-256を検証する。

## Agent側最小処理

```text
1. MQTTへ接続する
2. 対象トピックをsubscribeする
3. execution_idの重複実行を確認する
4. 必要ファイルを取得する
5. start APIを呼び出す
6. 実行コマンドを実行する
7. complete APIを呼び出す
```

## エラー時の扱い

| 事象 | 対応 |
| --- | --- |
| MQTT受信後に開始前で失敗 | 可能であればcomplete APIで `failed` を送信する。 |
| 実行中にAgentが停止 | バックエンドのタイムアウト監視で `timeout` とする。 |
| MQTT通知失敗 | 実行要求を `queued` のまま保持して再通知する。 |
| complete API重複送信 | 同一 `execution_id` への更新として扱う。 |
