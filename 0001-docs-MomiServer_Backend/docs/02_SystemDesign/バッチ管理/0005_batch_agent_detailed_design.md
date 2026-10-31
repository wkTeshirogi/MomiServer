# バッチAgent連携 詳細設計

## MQTT

### Broker

MQTT Brokerの接続先は環境設定で指定する。

```text
MQTT_BROKER_URL
MQTT_CLIENT_ID
MQTT_USERNAME
MQTT_PASSWORD
```

### Topic

```text
smarthome/agents/{agentId}/batch/command
```

`{agentId}` は対象Agent IDに置換される。

### QoS

QoSは `1` を基本とする。

Agent側ではQoS 1の重複配信に備え、`execution_id` 単位で二重実行を防止する。

## HTTP API

### 手動実行要求作成

```http
POST /api/batch/executions/definitions/{batchDefinitionId}
```

認証が必要。

リクエスト。

```json
{
  "target_agent_id": 1,
  "test_execution": false
}
```

レスポンス。

```json
{
  "id": 100,
  "execution_id": "f1a8f48b-8f63-4c8d-a5b2-47ef9b7563ec",
  "batch_definition_id": 10,
  "target_agent_id": 1,
  "requested_type": "manual",
  "execution_status": "dispatched",
  "execution_command": "python import.py"
}
```

### 実行要求詳細

```http
GET /api/batch/executions/{executionId}
```

認証が必要。

### Agent開始通知

```http
POST /api/v1/agent/batch/executions/{executionId}/start
```

認証不要。

状態遷移。

```text
queued/dispatched -> running
```

### Agent完了通知

```http
POST /api/v1/agent/batch/executions/{executionId}/complete
```

認証不要。

リクエスト。

```json
{
  "execution_status": "success",
  "exit_code": 0,
  "log_path": "logs/stdout.log",
  "message": "completed"
}
```

状態遷移。

```text
running -> success/failed/timeout/cancelled
```

完了通知後、実行履歴が保存される。

### 実行ファイル登録

```http
POST /api/batch/executions/{executionId}/files
```

認証が必要。

リクエスト。

```json
{
  "file_path": "D:/batch/scripts/import.py",
  "file_name": "import.py",
  "content_type": "text/x-python"
}
```

登録時にファイルサイズとSHA-256を計算する。

### Agentファイル一覧

```http
GET /api/v1/agent/batch/executions/{executionId}/files
```

認証不要。

### Agentファイルダウンロード

```http
GET /api/v1/agent/batch/executions/{executionId}/files/{fileId}/download
```

認証不要。

Agentはダウンロード後、レスポンスのファイルを実行用ディレクトリに保存する。

## DB項目

### 実行要求

| 項目 | 用途 |
|---|---|
| id | DB内部用ID |
| execution_id | Agent連携用UUID |
| batch_definition_id | バッチ定義ID |
| schedule_id | スケジュールID |
| target_agent_id | 対象Agent ID |
| requested_type | manual / schedule |
| execution_status | 実行状態 |
| execution_command | 実行コマンド |
| working_directory | 作業ディレクトリ |
| timeout_seconds | タイムアウト秒数 |
| max_retry_count | 最大リトライ回数 |
| retry_count | リトライ回数 |
| dispatch_attempt_count | MQTT通知試行回数 |
| scheduled_fire_time | スケジュール実行予定時刻 |

### 実行ファイル

| 項目 | 用途 |
|---|---|
| id | ファイルID |
| execution_request_id | 実行要求内部ID |
| file_name | Agent保存時のファイル名 |
| file_path | バックエンド上のファイルパス |
| content_type | Content-Type |
| file_size | ファイルサイズ |
| sha256 | SHA-256 |

## Agent実装ガイド

### 最小実装

Agentは最低限、以下を実装する。

```text
1. MQTT接続
2. command topicのsubscribe
3. execution_idの重複チェック
4. start API呼び出し
5. コマンド実行
6. complete API呼び出し
```

### ファイル取得ありの実装

ファイル取得が必要な場合は、start APIの前に以下を行う。

```text
1. ファイル一覧APIを呼ぶ
2. 各ファイルをダウンロードする
3. SHA-256を検証する
4. 作業ディレクトリへ配置する
```

### 二重実行防止

Agentは受信した `execution_id` をローカルに記録する。

同じ `execution_id` を再受信した場合、既に実行済みまたは実行中であれば再実行しない。

### タイムアウト

Agent側でも `timeout_seconds` を監視する。

タイムアウトした場合は、プロセスを停止し、complete APIで `execution_status = timeout` を送信する。

## エラー時の扱い

### MQTT受信後にstart送信前で失敗

Agentは可能であればcomplete APIで `failed` を送信する。

### 実行中にAgentが停止

バックエンドのタイムアウト監視により、一定時間後に `timeout` になる。

### MQTT通知失敗

バックエンドは `queued` のまま保持し、定期的に再通知する。

### complete APIの重複送信

Agentはcomplete APIの重複送信を避ける。バックエンド側では同じ `execution_id` への更新として扱われる。

## 将来拡張

- Agent専用Bearer以外の追加認証方式（必要性・移行方針は未決定）
- MQTTメッセージ署名
- Agent heartbeat
- 実行ログアップロード
- リトライ実行
- キャンセル指示
- AgentからバックエンドへのMQTT結果通知
