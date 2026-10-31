# バッチ管理 基本設計

## 概要

バッチ管理は、バッチ定義、バッチスケジュール、実行要求、実行履歴を管理し、対象Agentへバッチ実行を依頼する。

実行状態の正本はDBとし、MQTTはバックエンドからAgentへの実行通知に利用する。Agentからの開始・完了通知はHTTPで受け付ける。

## 全体構成

```text
Frontend
  |
  | バッチ定義管理 / スケジュール管理 / 手動実行
  v
Backend
  |
  | 定義・スケジュール・実行要求・履歴を管理
  v
Database
  |
  | 実行通知
  v
MQTT Broker
  |
  | command topic
  v
Agent
  |
  | start / complete
  v
Backend
```

## 構成要素

| 構成要素 | 役割 |
| --- | --- |
| バッチ定義 | 実行種別、実行コマンド、作業ディレクトリ、ログ出力先、リトライ、通知設定を管理する。 |
| バッチ定義パラメータ | バッチ定義に紐づく実行パラメータを管理する。 |
| バッチスケジュール | バッチ定義、対象Agent、Cron式、実行期間、休日実行可否、優先度を管理する。 |
| 実行要求 | 手動またはスケジュールから作成される実行単位を管理する。 |
| 実行履歴 | 実行結果、終了コード、実行時間、ログ参照情報を管理する。 |
| MQTT通知 | 対象Agentへ実行要求を通知する。 |
| Agent通知API | Agentからの開始・完了通知を受け付ける。 |
| ファイル配布API | Agentが実行関連ファイルを取得するためのインターフェースを提供する。 |

## 実行要求作成種別

| 種別 | 作成元 | 内容 |
| --- | --- | --- |
| manual | 管理者操作 | バッチ定義を指定して即時実行要求を作成する。 |
| schedule | スケジュール監視 | Cron式に一致したバッチスケジュールから実行要求を作成する。 |

## 実行権限制御

手動実行は、バッチ定義ごとの `required_role` を利用して実行可否を判定する。

| 実行主体 | 判定 |
| --- | --- |
| `ADMIN` | すべてのバッチ定義を実行可能 |
| `USER` | `required_role` が `USER` のバッチ定義のみ実行可能 |
| `BAN_USER` | 実行不可 |
| 未認証 | 実行不可 |

バックエンドでは `@CanExecuteBatch`、フロントエンドでは `RequireBatchExecution` を利用して、同じ考え方で制御する。

```java
@CanExecuteBatch
@PostMapping("/definitions/{batchDefinitionId}")
public ResponseEntity<?> createManualExecution(@PathVariable Long batchDefinitionId) {
}
```

```tsx
<RequireBatchExecution requiredRole={batchDefinition.required_role}>
  <Button>実行</Button>
</RequireBatchExecution>
```

詳細は [認可アノテーション実装設計](../../11_SecurityDesign/0005_authorization_annotation.md) と
[フロントエンド権限制御設計](../../06_UserInterfaceDesign/3005_frontend_authorization.md) を参照する。

## 実行要求作成時のコピー項目

実行要求作成時には、実行時点のバッチ定義を固定するため、以下の項目を実行要求へコピーする。

| 項目 | 内容 |
| --- | --- |
| execution_command | 実行コマンド |
| working_directory | 作業ディレクトリ |
| timeout_seconds | タイムアウト秒数 |
| max_retry_count | 最大リトライ回数 |
| retry_interval_seconds | リトライ間隔秒数 |
| log_path | ログ出力先 |

## 状態遷移

```text
queued
  |
  | MQTT通知成功
  v
dispatched
  |
  | Agent開始通知
  v
running
  |
  | Agent完了通知
  v
success / failed / timeout / cancelled
```

MQTT通知に失敗した場合、実行要求は `queued` のまま保持し、再通知対象とする。

バックエンド実装では `BatchExecutionStatus` 列挙型で状態値を扱う。
DB保存値およびAPI入出力値は従来通り `queued`、`dispatched`、`running`、`success`、`failed`、
`timeout`、`cancelled` の文字列とする。

## スケジュール実行判定

| 判定条件 | 内容 |
| --- | --- |
| スケジュール有効 | スケジュールが有効であること。 |
| バッチ定義有効 | 紐づくバッチ定義が有効であること。 |
| 実行開始日 | 現在日付が開始日以降であること。 |
| 実行終了日 | 現在日付が終了日以前であること。 |
| Cron式 | 現在時刻がCron式に一致すること。 |
| 重複防止 | 同一スケジュール、同一予定時刻の実行要求が未作成であること。 |

## 通信設計

| 通信 | 方向 | 用途 |
| --- | --- | --- |
| 管理API | FrontendからBackend | 定義、スケジュール、手動実行、履歴参照を行う。 |
| MQTT | BackendからAgent | バッチ実行要求を通知する。 |
| Agent API | AgentからBackend | 開始通知、完了通知を受け付ける。 |
| ファイル配布API | AgentからBackend | 実行関連ファイルを配布する。 |

## 識別子設計

| 識別子 | 用途 |
| --- | --- |
| id | DB内部管理用の連番ID。 |
| execution_id | Agent連携、MQTT、Agent API、ファイル取得URLで使用するUUID。 |

Agent連携では、連番IDではなく `execution_id` を使用する。

## スケジュール実行状況の参照

管理者向けに、DBに生成済みの実行要求を予定日・時間帯・状態別に集計する。
完了ログの参照は別APIに分離し、ページ単位で取得する。画面参照によって実行要求は生成しない。
検索条件とデータ取得は[スケジュール詳細設計](Details/0002_batch_schedule_detail_design.md)を参照する。

## 実行エラーへの対応

| 事象 | 対応 |
| --- | --- |
| MQTT通知失敗 | 実行状態を `queued` のまま保持し、再通知対象にする。 |
| Agent開始通知前の失敗 | 可能であればAgentから完了通知で `failed` を送信する。 |
| Agent実行中の停止 | バックエンドのタイムアウト監視により `timeout` とする。 |
| 完了通知の重複 | 同一 `execution_id` への状態更新として扱う。 |

