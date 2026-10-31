# バッチ定義一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0015 |
| 分類 | バッチ定義マスタ |
| メソッド | POST |
| パス | `/api/master/batch/list` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

登録済みのバッチ定義一覧を取得する。

## リクエスト

リクエストボディなし。

## レスポンス

バッチ定義詳細の配列を返す。

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | バッチ定義ID |
| batch_name | string | バッチ名 |
| view_name | string | 表示名 |
| description | string | 説明 |
| enabled | boolean | 有効状態 |
| category | string | カテゴリ |
| tags | string | タグ |
| execution_type | string | 実行種別 |
| execution_command | string | 実行コマンド |
| working_directory | string | 作業ディレクトリ |
| timeout_seconds | number | タイムアウト秒数 |
| max_retry_count | number | 最大リトライ回数 |
| retry_interval_seconds | number | リトライ間隔秒数 |
| execution_user | string | 実行ユーザー |
| required_role | string | 必要権限 |
| priority | number | 優先度 |
| allow_parallel_execution | boolean | 並列実行可否 |
| log_retention_date | string | ログ保持期限 |
| log_path | string | ログパス |
| success_notification | string | 成功時通知 |
| failure_notification | string | 失敗時通知 |
| createdAt | string | 作成日時 |
| updatedAt | string | 更新日時 |

