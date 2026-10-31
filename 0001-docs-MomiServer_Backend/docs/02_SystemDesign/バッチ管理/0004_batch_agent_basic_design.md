# バッチAgent連携 基本設計

## 全体構成

```text
Frontend
  |
  | 手動実行要求
  v
Backend
  |
  | 実行要求作成
  v
DB
  |
  | MQTT publish
  v
Agent
  |
  | start / complete
  v
Backend
  |
  | 状態更新 / 履歴保存
  v
DB
```

## 実行要求作成

実行要求は以下の2種類から作成される。

| 種別 | 作成元 |
|---|---|
| manual | フロントエンドからの手動実行 |
| schedule | スケジュール監視処理 |

実行要求作成時、以下の情報をバッチ定義からコピーする。

- 実行コマンド
- 作業ディレクトリ
- タイムアウト秒数
- 最大リトライ回数
- ログパス

## MQTT通知

バックエンドは実行要求作成後、対象Agentのトピックへ実行要求をpublishする。

```text
smarthome/agents/{agentId}/batch/command
```

publishに成功した場合、実行状態は `dispatched` になる。

publishに失敗した場合、実行状態は `queued` のまま保持される。

## MQTT Payload

Agentは以下のようなJSONを受信する。

```json
{
  "id": 100,
  "execution_id": "f1a8f48b-8f63-4c8d-a5b2-47ef9b7563ec",
  "batch_definition_id": 10,
  "schedule_id": null,
  "target_agent_id": 1,
  "requested_type": "manual",
  "requested_by": "admin@example.com",
  "execution_status": "queued",
  "test_execution": false,
  "execution_command": "python import.py",
  "working_directory": "D:/batch",
  "timeout_seconds": 300,
  "max_retry_count": 0,
  "retry_count": 0,
  "dispatch_attempt_count": 0,
  "log_path": "logs/import.log",
  "scheduled_fire_time": null,
  "requested_at": "2026-08-11T14:00:00"
}
```

Agentは外部連携IDとして `execution_id` を使用する。

## Agent側処理フロー

```text
1. MQTTトピックをsubscribe
2. 実行要求を受信
3. execution_id の重複実行を確認
4. 必要ファイル一覧を取得
5. 必要ファイルをダウンロード
6. start APIを呼び出す
7. execution_command を実行
8. complete APIを呼び出す
```

## 開始通知

```http
POST /api/v1/agent/batch/executions/{executionId}/start
```

リクエストbodyは不要。

成功時、実行状態は `running` になる。

## 完了通知

```http
POST /api/v1/agent/batch/executions/{executionId}/complete
```

```json
{
  "execution_status": "success",
  "exit_code": 0,
  "log_path": "logs/import.log",
  "message": "completed"
}
```

`execution_status` に指定できる値は以下。

| 値 | 意味 |
|---|---|
| success | 正常終了 |
| failed | 異常終了 |
| timeout | Agent側タイムアウト |
| cancelled | キャンセル |

完了通知後、バックエンドは実行履歴を保存する。

## ファイル配布

ファイル本体はHTTPで取得する。

### ファイル一覧

```http
GET /api/v1/agent/batch/executions/{executionId}/files
```

レスポンス例。

```json
[
  {
    "id": 1,
    "execution_request_id": 100,
    "file_name": "import.py",
    "content_type": "text/x-python",
    "file_size": 1024,
    "sha256": "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
  }
]
```

### ファイルダウンロード

```http
GET /api/v1/agent/batch/executions/{executionId}/files/{fileId}/download
```

Agentはレスポンスbodyをファイルとして保存する。

## スケジュール実行

バックエンドは定期的にスケジュールを確認する。

判定条件は以下。

- スケジュールが有効
- バッチ定義が有効
- 現在日付が開始日以降
- 現在日付が終了日以前
- cron式が現在時刻に一致

一致した場合、`requested_type = schedule` の実行要求を作成する。

同じ `schedule_id` と `scheduled_fire_time` の組み合わせは重複登録しない。

## 再通知

`queued` の実行要求は定期的にMQTT再通知の対象になる。

MQTT通知が成功すると `dispatched` になる。

## タイムアウト監視

`running` の実行要求について、開始時刻から `timeout_seconds` を超過した場合は `timeout` に更新する。

タイムアウト更新時も実行履歴を保存する。
