# バッチ実行ファイル登録 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0025 |
| 分類 | バッチ実行ファイル |
| メソッド | POST |
| パス | `/api/batch/executions/{executionId}/files` |
| 認証 | 必須 |
| 権限 | ADMIN |
| 正常ステータス | HTTP 200 |

## 概要

バッチ実行要求に紐づくファイルを登録する。
登録時にサーバー上のファイル存在を確認し、ファイルサイズと SHA-256 を算出して保存する。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| executionId | string(UUID) | 必須 | バッチ実行要求 UUID |

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| file_path | string | 必須 | 登録対象ファイルのパス。最大 1000 文字 |
| file_name | string | 任意 | download 時の表示ファイル名。最大 255 文字 |
| content_type | string | 任意 | Content-Type。最大 255 文字 |

## レスポンス

`BatchExecutionFileDetailDto` を返却する。

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | ファイル ID |
| execution_request_id | number | 実行要求 ID |
| file_name | string | ファイル名 |
| file_path | string | 正規化後の絶対パス |
| content_type | string | Content-Type |
| file_size | number | ファイルサイズ |
| sha256 | string | SHA-256 ハッシュ |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| 未認証 | 401 | JWT がない、または無効 |
| 権限不足 | 403 | ADMIN 権限がない |
| 実行要求が存在しない | 404 | 指定 UUID の実行要求がない |
| ファイルが存在しない | 404 | 指定パスが通常ファイルではない |

