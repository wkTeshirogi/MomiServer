# m_batch_definitions

## 概要

バッチ処理の実行定義、リトライ、並列実行、ログ、通知に関する設定を管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | バッチ定義ID |
| 2 | batch_name | VARCHAR(255) | 不可 | なし | 一意 | バッチ名 |
| 3 | view_name | VARCHAR(255) | 不可 | なし | なし | 表示名 |
| 4 | description | TEXT | 可 | なし | なし | 説明 |
| 5 | enabled | BOOLEAN | 不可 | true | なし | 有効状態 |
| 6 | category | VARCHAR(50) | 不可 | なし | なし | カテゴリ |
| 7 | tags | VARCHAR(500) | 可 | なし | なし | タグ |
| 8 | execution_type | VARCHAR(50) | 不可 | なし | なし | 実行種別 |
| 9 | execution_command | VARCHAR(1000) | 不可 | 空文字 | なし | 実行コマンド |
| 10 | working_directory | VARCHAR(500) | 可 | なし | なし | 作業ディレクトリ |
| 11 | timeout_seconds | INTEGER | 不可 | 0 | 0以上 | タイムアウト秒数 |
| 12 | max_retry_count | INTEGER | 不可 | 0 | 0から9 | 最大リトライ回数 |
| 13 | retry_interval_seconds | INTEGER | 可 | なし | 0以上 | リトライ間隔秒数 |
| 14 | execution_user | VARCHAR(255) | 可 | なし | なし | 実行ユーザー |
| 15 | required_role | VARCHAR(50) | 可 | なし | なし | 必要権限 |
| 16 | priority | INTEGER | 不可 | 5 | 1から9 | 優先度 |
| 17 | allow_parallel_execution | BOOLEAN | 不可 | false | なし | 並列実行可否 |
| 18 | log_retention_date | DATE | 不可 | 2999-12-31 | なし | ログ保持期限 |
| 19 | log_path | VARCHAR(500) | 不可 | なし | なし | ログパス |
| 20 | success_notification | VARCHAR(50) | 不可 | none | なし | 成功時通知 |
| 21 | failure_notification | VARCHAR(50) | 不可 | none | なし | 失敗時通知 |
| 22 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 23 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |
| 24 | version | BIGINT | 不可 | 1 | 1以上 | 楽観的ロック用更新番号 |
| 25 | status | VARCHAR(20) | 不可 | active | active、deleted。deleted時はenabled=false | 定義のライフサイクル状態 |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_m_batch_definitions_enabled | enabled | 有効状態検索 |
| 2 | idx_m_batch_definitions_category | category | カテゴリ検索 |
| 3 | idx_m_batch_definitions_execution_type | execution_type | 実行種別検索 |
| 4 | idx_m_batch_definitions_priority | priority | 優先度検索 |
| 5 | idx_m_batch_definitions_updated_dt | updated_dt | 更新日時順検索 |
| 6 | idx_m_batch_definitions_status | status | 論理削除を除外する検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新し、`version`を1加算する。
更新時は取得時の`version`を条件に含め、更新件数が0件の場合は競合として扱う。

## マスタ削除制御

バッチ定義を通常利用から除外する場合は、物理削除ではなくマスタステータスを削除扱いの状態へ更新する。
削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。
`enabled` は有効 / 無効の切り替えに限定し、削除状態の管理には使用しない。
論理削除は`status = deleted`で表し、通常の一覧取得から除外する。Phase 1では削除済み定義の復元操作を提供しない。
