# バッチ定義登録・更新 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0017 |
| 分類 | バッチ定義マスタ |
| メソッド | POST |
| パス | `/api/master/batch/register` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

バッチ定義を登録または更新し、登録後の詳細情報を返す。
実行に必要な入力ファイル、実行スクリプト、設定ファイルは、定義保存後に添付APIで即時確定する。

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| id | number | 任意 | 更新対象ID |
| version | number | 更新時必須 | 取得時の更新番号。登録時は省略 |
| status | string | 任意 | active、deleted。省略時はactive |
| batch_name | string | 必須 | バッチ名 |
| view_name | string | 必須 | 表示名 |
| description | string | 任意 | 説明 |
| enabled | boolean | 必須 | 有効状態 |
| category | string | 必須 | カテゴリ |
| tags | string | 任意 | タグ |
| execution_type | string | 必須 | 実行種別 |
| execution_command | string | 必須 | 実行コマンド |
| working_directory | string | 任意 | 作業ディレクトリ |
| timeout_seconds | number | 必須 | タイムアウト秒数 |
| max_retry_count | number | 必須 | 最大リトライ回数 |
| retry_interval_seconds | number | 任意 | リトライ間隔秒数 |
| execution_user | string | 任意 | 実行ユーザー |
| required_role | string | 任意 | 必要権限 |
| priority | number | 必須 | 優先度 |
| allow_parallel_execution | boolean | 必須 | 並列実行可否 |
| log_retention_date | string | 必須 | ログ保持期限 |
| log_path | string | 必須 | ログパス |
| success_notification | string | 必須 | 成功時通知 |
| failure_notification | string | 必須 | 失敗時通知 |

## レスポンス

バッチ定義一覧取得APIの1要素と同じ。

## 更新・削除仕様

1. 更新時は`id`と`version`を照合し、内容更新と`version`加算を同一SQLで行う。
2. 更新番号が一致しない場合はHTTP 409、エラーコード`OPTIMISTIC_LOCK_CONFLICT`を返す。
3. 有効から無効へ変更した場合、未開始の実行要求を取消対象にする。既に実行中の要求は停止しない。
4. `status = deleted`は、無効化済みで未完了要求がない場合だけ受け付ける。
5. 論理削除済みの定義は一覧から除外する。履歴と作成済み実行要求は保持する。

## エラー

| HTTP | コード | 条件 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 状態値が不正、削除前提を満たさない、未完了要求が存在する |
| 404 | RESOURCE_NOT_FOUND | 対象定義が存在しない、または既に削除済み |
| 409 | OPTIMISTIC_LOCK_CONFLICT | 取得後に他の操作で更新されている |

