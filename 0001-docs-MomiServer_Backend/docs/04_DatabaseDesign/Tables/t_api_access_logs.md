# t_api_access_logs

## 概要

HTTP APIへのアクセス結果を、機密情報を含まない共通形式で追記保存する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGINT | 不可 | IDENTITY | 主キー | DB内部ID |
| 2 | request_id | UUID | 不可 | なし | UNIQUE | 外部参照用リクエストID |
| 3 | occurred_at | TIMESTAMPTZ | 不可 | なし | なし | API受付日時 |
| 4 | http_method | VARCHAR(10) | 不可 | なし | なし | HTTPメソッド |
| 5 | request_path | TEXT | 不可 | なし | なし | クエリ文字列を除くリクエストパス |
| 6 | endpoint_pattern | VARCHAR(1024) | 可 | なし | なし | パス変数展開前のエンドポイント |
| 7 | status_code | SMALLINT | 不可 | なし | 100～599 | HTTPステータス |
| 8 | duration_ms | BIGINT | 不可 | なし | 0以上 | 処理時間（ミリ秒） |
| 9 | source_type | VARCHAR(20) | 不可 | なし | CHECK | 発生元分類 |
| 10 | principal | VARCHAR(255) | 可 | なし | なし | 認証済み操作主体 |
| 11 | ip_address | VARCHAR(45) | 可 | なし | なし | 接続元IPv4またはIPv6 |
| 12 | user_agent | VARCHAR(512) | 可 | なし | なし | User-Agent |
| 13 | error_code | VARCHAR(100) | 可 | なし | なし | 共通エラーコード |
| 14 | created_dt | TIMESTAMPTZ | 不可 | now() | なし | DB登録日時 |

## 発生元分類

| 値 | 内容 |
| --- | --- |
| web | Webユーザーからのアクセス |
| agent | Agent専用APIへのアクセス |
| iot | IoT APIへのアクセス |
| gateway | Gateway経由アクセス |
| anonymous | 認証されていないアクセス |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_api_access_logs_request_id_key | request_id | はい | 詳細取得 |
| 2 | idx_t_api_access_logs_occurred | occurred_at DESC | いいえ | 時系列検索、保持期間削除 |
| 3 | idx_t_api_access_logs_status_occurred | status_code, occurred_at DESC | いいえ | HTTPステータス検索 |
| 4 | idx_t_api_access_logs_source_occurred | source_type, occurred_at DESC | いいえ | 発生元検索 |
| 5 | idx_t_api_access_logs_principal_occurred | principal, occurred_at DESC | いいえ | 操作主体検索 |

## 外部キー

外部キーは設定しない。ユーザーやAgentが削除された後も当時の操作主体文字列を監査・障害調査用に保持する。

## 更新制御

追記専用とし、更新APIおよび削除APIは提供しない。保持期間削除だけを定期処理から実施する。

## 備考

- リクエスト・レスポンス本文、認証ヘッダー、Cookie、APIキー、未加工のクエリ文字列は保存しない。
- 初期マイグレーションは`V32__create_api_access_logs.sql`で管理する。
