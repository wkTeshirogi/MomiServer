# バッチAgent連携 要件定義

## 目的

バックエンドで管理しているバッチ定義およびスケジュール定義をもとに、対象Agentへ実行指示を送信し、Agentが対象マシン上でバッチ処理を実行できるようにする。

## 対象範囲

- バックエンドからAgentへのバッチ実行指示
- Agentからバックエンドへの実行開始通知
- Agentからバックエンドへの実行完了通知
- Agentによる実行関連ファイルの取得
- バッチスケジュールに基づく自動実行要求の作成
- 実行状態、実行履歴、タイムアウトの管理

## 対象外

- Agent本体のインストーラー作成
- Agentの常駐プロセス管理
- Agent端末のOS別サービス登録
- Agent認証キー、署名検証、証明書認証
- 実行ログ全文のアップロード
- AgentからバックエンドへのMQTT通知

## 基本方針

バッチ実行状態の正本はDBとし、MQTTはバックエンドからAgentへの通知に使用する。

```text
Frontend
  -> Backend
  -> DBに実行要求を作成
  -> MQTTでAgentへ通知
  -> Agentが実行
  -> AgentがHTTPで開始/完了を通知
  -> BackendがDBを更新
```

## 識別子

Agentとの通信では、連番IDではなくUUID形式の `execution_id` を使用する。

```text
内部管理ID: id
外部連携ID: execution_id
```

Agent、MQTT、HTTP API、ファイル取得URLでは `execution_id` を使用する。

## 実行状態

| 状態 | 意味 |
|---|---|
| queued | 実行要求作成済み。Agentへ未通知または再通知待ち |
| dispatched | AgentへMQTT通知済み |
| running | Agentが実行開始済み |
| success | 正常終了 |
| failed | 異常終了 |
| timeout | タイムアウト |
| cancelled | キャンセル |

## 通信要件

### バックエンドからAgent

MQTTで実行要求を送信する。

```text
smarthome/agents/{agentId}/batch/command
```

### Agentからバックエンド

HTTPで開始通知と完了通知を送信する。

```text
POST /api/v1/agent/batch/executions/{executionId}/start
POST /api/v1/agent/batch/executions/{executionId}/complete
```

この2つのAPIはAgentから利用するため、認証なしで呼び出せる。

## ファイル配布要件

ファイル本体はMQTTで送信しない。AgentはHTTPでファイル一覧を取得し、必要なファイルをダウンロードする。

```text
GET /api/v1/agent/batch/executions/{executionId}/files
GET /api/v1/agent/batch/executions/{executionId}/files/{fileId}/download
```

Agentはダウンロード後、必要に応じてSHA-256を検証する。

## スケジュール実行要件

バックエンドは定期的にバッチスケジュールを確認し、cron式に該当する場合は実行要求を作成する。

同一スケジュールの同一実行予定時刻については、重複登録しない。

## タイムアウト要件

Agentが開始通知を送った後、定義されたタイムアウト秒数を超過した場合、バックエンドは実行要求を `timeout` に更新し、実行履歴を保存する。

## 再通知要件

MQTT publishに失敗した実行要求は `queued` のまま保持し、バックエンドが定期的に再通知を試行する。

## Agent側に求める動作

- 自分のAgent IDに対応するMQTTトピックをsubscribeする
- 実行要求を受信したら `execution_id` を記録する
- 同じ `execution_id` を重複受信しても二重実行しない
- 実行開始時にstart APIを呼ぶ
- 実行完了時にcomplete APIを呼ぶ
- ファイルが必要な場合はHTTPで取得する
- タイムアウトはAgent側でも監視する
