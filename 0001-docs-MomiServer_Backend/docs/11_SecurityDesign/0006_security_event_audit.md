# 不正アクセス監査イベント設計

## 概要

不正アクセス、認証失敗、認可拒否、レート制限超過などのセキュリティ上重要なイベントは、通常のアプリケーションログとは別に専用の監査イベントとして記録する。

監査イベントの正本はDBとし、Discord通知およびフロントエンド通知はDBに記録されたイベントを利用者へ知らせるための補助経路とする。

## 目的

- 不正アクセスの発生状況を後から検索、確認、監査できるようにする。
- Gateway API、JWT認証、権限制御で発生した拒否イベントを共通形式で記録する。
- 重要度の高いイベントをDiscordへ通知し、早期に気付けるようにする。
- フロントエンド管理画面で未確認イベント、検索、詳細確認、対応状況管理を行えるようにする。
- 保持期間を定義し、監査性と保存容量のバランスを取る。

## 記録対象イベント

| イベント種別 | 発生箇所 | HTTPステータス | 重要度 | 通知方針 |
| --- | --- | --- | --- | --- |
| `invalid_api_key` | Gateway APIキー認証 | 401 | warning | 短時間に連続した場合はDiscord通知 |
| `rate_limit_exceeded` | Gatewayレート制限 | 429 | warning | Discord通知対象 |
| `jwt_invalid` | JWT認証 | 401 | warning | 原則DB記録のみ |
| `access_denied` | 認可制御 | 403 | warning | 管理APIの場合はDiscord通知対象 |
| `login_failed` | ログイン認証 | 401 | info / warning | 連続失敗時にDiscord通知 |
| `admin_access_denied` | 管理者API認可 | 403 | warning | Discord通知対象 |
| `critical_attack_pattern` | 集計・検知処理 | - | critical | Discord即時通知対象 |

## 記録先

初期実装では `t_security_events` の1テーブルに集約する。

通知履歴、詳細メタデータ、対応履歴を分離する必要が出た時点で、`t_security_event_notifications` や `t_security_event_actions` へ分割する。

## テーブル設計方針

| カラム | 内容 | 備考 |
| --- | --- | --- |
| `id` | DB内部ID | 連番 |
| `event_id` | 外部参照用ID | UUID |
| `event_type` | イベント種別 | `invalid_api_key` など |
| `severity` | 重要度 | `info` / `warning` / `critical` |
| `source_type` | 発生元 | `web` / `agent` / `iot` / `gateway` |
| `principal` | 操作主体 | email、APIキー識別子、anonymousなど |
| `ip_address` | 送信元IP | 検索・集計に使用 |
| `user_agent` | User-Agent | 任意 |
| `http_method` | HTTPメソッド | 任意 |
| `request_path` | リクエストパス | 任意 |
| `status_code` | HTTPステータス | 401 / 403 / 429など |
| `reason` | 記録理由 | 表示・調査用 |
| `metadata_json` | 補足情報 | APIバージョン、clientTypeなど |
| `notification_status` | 通知状態 | `pending` / `sent` / `suppressed` / `failed` |
| `ack_status` | 確認状態 | `new` / `acknowledged` / `investigating` / `resolved` / `ignored` |
| `acknowledged_by` | 確認者 | 管理者email |
| `acknowledged_at` | 確認日時 | 任意 |
| `created_dt` | 発生日時 | 検索・保持期間判定に使用 |
| `updated_dt` | 更新日時 | 状態更新時に更新 |

APIキーそのものは保存しない。APIキーに紐づく識別子を保存する場合は、ハッシュ値またはマスク済み文字列を利用する。

## 検索設計

管理画面では、セキュリティイベント一覧と詳細画面を提供する。

### 検索条件

| 条件 | 内容 |
| --- | --- |
| 発生日時From / To | `created_dt` の範囲検索 |
| イベント種別 | `event_type` |
| 重要度 | `severity` |
| 発生元 | `source_type` |
| 送信元IP | `ip_address` |
| 操作主体 | `principal` |
| リクエストパス | `request_path` の部分一致 |
| HTTPステータス | `status_code` |
| 確認状態 | `ack_status` |
| 通知状態 | `notification_status` |

### 一覧表示項目

| 項目 | 内容 |
| --- | --- |
| 発生日時 | `created_dt` |
| 重要度 | `severity` |
| イベント種別 | `event_type` |
| 発生元 | `source_type` |
| 操作主体 | `principal` |
| 送信元IP | `ip_address` |
| パス | `request_path` |
| ステータス | `status_code` |
| 確認状態 | `ack_status` |

### 推奨インデックス

| インデックス | 目的 |
| --- | --- |
| `created_dt` | 時系列検索、保持期間削除 |
| `event_type, created_dt` | 種別ごとの時系列検索 |
| `severity, created_dt` | 重要度ごとの確認 |
| `source_type, created_dt` | 発生元ごとの確認 |
| `ip_address, created_dt` | 同一IPからの連続アクセス調査 |
| `principal, created_dt` | 操作主体ごとの監査 |
| `ack_status, created_dt` | 未確認イベント一覧 |

## 保持期間設計

| データ | 保持期間 | 方針 |
| --- | --- | --- |
| 通常イベント | 180日 | 日次バッチで削除 |
| `critical` イベント | 1年 | 監査用に長めに保持 |
| 集計結果 | 2年 | 傾向分析用に保持 |
| 詳細メタデータ | 90日 | 保存容量と個人情報リスクを抑える |
| 通知履歴 | 180日 | 通知失敗や抑止判断の確認用 |

削除APIは作成せず、保持期間を過ぎたイベントは運用バッチで削除する。

## 監査用途

監査では、発生イベントを削除せず、確認状態と対応状態を更新して履歴として扱う。

| 状態 | 内容 |
| --- | --- |
| `new` | 未確認 |
| `acknowledged` | 管理者が確認済み |
| `investigating` | 調査中 |
| `resolved` | 対応完了 |
| `ignored` | 誤検知または対応不要 |

監査時は、イベント発生日時、操作主体、送信元IP、対象パス、拒否理由、確認者、確認日時を確認できるようにする。

## Discord通知設計

Discord通知は、重要度が高いイベントや短時間に連続する不正アクセスを対象とする。

初期実装ではDiscord Webhookによる一方向通知を採用し、Discord Botは使用しない。

Botを利用するのは、Discord側から確認済み化、対応状態更新、再通知、検索などの双方向操作が必要になった場合に限定する。

### 設定方針

| 設定キー | 環境変数 | 既定値 | 内容 |
| --- | --- | --- | --- |
| `notification.discord.enabled` | `DISCORD_NOTIFICATION_ENABLED` | `false` | Discord通知全体の有効 / 無効 |
| `notification.discord.webhook-url` | `DISCORD_WEBHOOK_URL` | 空文字 | Discord Webhook URL |
| `notification.discord.security-events.enabled` | `SECURITY_EVENT_DISCORD_NOTIFICATION_ENABLED` | `false` | 不正アクセス監査イベント通知の有効 / 無効 |
| `notification.discord.security-events.minimum-severity` | `SECURITY_EVENT_DISCORD_MINIMUM_SEVERITY` | `warning` | 通知対象とする最低重要度 |
| `notification.discord.security-events.aggregation-window-seconds` | `SECURITY_EVENT_DISCORD_AGGREGATION_WINDOW_SECONDS` | `300` | 同種イベントを集約通知する時間窓 |

`DISCORD_WEBHOOK_URL` は機密情報として環境変数で管理し、設定ファイルやソースコードへ直接記載しない。

### 実装サンプル

Discord通知は公開窓口である `Notifications` から送信する。

`DiscordNotice` は `common.util` パッケージ内の内部実装として扱い、他パッケージから直接参照しない。

不正アクセス監査イベントの記録処理では、DBへの監査イベント保存を正本とし、Discord通知は保存後の補助通知として呼び出す。

```java
notifications.sendDiscordNotice(
        "Security Event",
        "rate_limit_exceeded: /api/gateway/device-telemetry",
        NoticeLevel.WARNING);
```

通知本文には、イベント種別、対象パス、重要度、発生元など調査に必要な情報のみを含める。

APIキー、JWT、パスワード、Webhook URLなどの機密値は通知本文、例外ログ、監査メタデータへ含めない。

### 通知対象

| 条件 | 通知 |
| --- | --- |
| `severity = critical` | 即時通知 |
| `rate_limit_exceeded` | 通知 |
| 管理APIへの `access_denied` | 通知 |
| `invalid_api_key` が短時間に連続 | 集約して通知 |
| `login_failed` が短時間に連続 | 集約して通知 |

### 通知抑止

| 条件 | 方針 |
| --- | --- |
| 単発の期限切れJWT | DB記録のみ |
| 同一イベントの短時間連続 | 集約通知 |
| 通知済みイベント | 再通知しない |
| 低重要度イベント | フロントエンド一覧で確認 |

DiscordにはAPIキー、JWT、パスワードなどの機密値を送信しない。

## フロントエンド通知設計

フロントエンドでは、管理者向けにセキュリティイベントを表示する。

| 機能 | 内容 |
| --- | --- |
| ヘッダー通知 | 未確認のwarning / critical件数を表示 |
| イベント一覧 | 検索条件に基づいて監査イベントを一覧表示 |
| イベント詳細 | metadata、理由、通知状態、確認状態を表示 |
| 確認済み化 | `ack_status` を `acknowledged` に更新 |
| 対応状態更新 | `investigating` / `resolved` / `ignored` へ更新 |

ブラウザ通知は `t_notifications` と `t_user_notifications` で管理し、不正アクセス監査イベントを発生元とする通知は `source_type = 'security_event'` として作成する。

未読バッジはユーザー別通知状態の `read_status = 'unread'` を利用する。

初期実装ではポーリングで十分とする。リアルタイム性が必要になった場合は、WebSocketまたはSSEで未確認件数を配信する。

## API設計方針

| API | 用途 | 権限 |
| --- | --- | --- |
| `/api/security/events/list` | イベント一覧検索 | 管理者 |
| `/api/security/events/detail` | イベント詳細取得 | 管理者 |
| `/api/security/events/summary` | 未確認件数・重要度別件数取得 | 管理者 |
| `/api/security/events/acknowledge` | 確認済み化 | 管理者 |
| `/api/security/events/status` | 対応状態更新 | 管理者 |

削除APIは作成しない。保持期間による削除は運用バッチで行う。

## 実装順序

| 順序 | 内容 |
| --- | --- |
| 1 | `t_security_events` テーブルを追加する |
| 2 | `SecurityEventRepository` と `SecurityEventService` を追加する |
| 3 | Gateway APIキー不正、レート制限超過を記録する |
| 4 | JWT不正、認可拒否、ログイン失敗を記録する |
| 5 | Discord通知サービスを追加する |
| 6 | セキュリティイベント一覧・詳細画面を追加する |
| 7 | 保持期間削除バッチを追加する |
| 8 | 必要に応じてWebSocketまたはSSE通知へ拡張する |

## 関連ドキュメント

- [認可設計](./0002_authorization.md)
- [セキュリティエラー応答設計](./0004_security_error_response.md)
- [API Gateway 基本設計](../02_SystemDesign/API%20Gateway/0001_gateway_basic_design.md)
- [ログ設計](../10_LoggingAndMonitoringDesign/0001_logging.md)
- [t_notifications](../04_DatabaseDesign/Tables/t_notifications.md)
- [t_user_notifications](../04_DatabaseDesign/Tables/t_user_notifications.md)
