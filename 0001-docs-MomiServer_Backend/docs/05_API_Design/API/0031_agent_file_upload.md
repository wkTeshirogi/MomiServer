# Agent ファイルアップロード API

> 状態: 旧設計案・未実装。このパスは現行APIとして提供していない。以下の入出力・例外は実装済みの契約ではない。
> 実装を再検討する場合はAgent専用Bearerと対象実行要求の端末・登録世代の認可を前提とする。

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0031 |
| 分類 | Agent ファイル転送 |
| メソッド | POST |
| パス | `/api/v1/agent/upload/{category}` |
| 認証 | 旧案: Gateway APIキー。現行方式では不可、再設計が必要 |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent がバッチ実行に関連するログまたは成果物をアップロードする。
論理パスは `/upload/{category}` とし、既存の外部連携 API プレフィックスに合わせて `/api/v1/agent` 配下で提供する。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| category | string | 必須 | アップロードカテゴリ。`log` または `artifact` |

## リクエスト

`multipart/form-data` で送信する。

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| execution_id | string(UUID) | 必須 | バッチ実行要求 UUID |
| file_name | string | 必須 | ファイル名 |
| content_type | string | 任意 | Content-Type |
| checksum_sha256 | string | 任意 | SHA-256 |
| file | file | 必須 | アップロード対象ファイル |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| file_id | number | ファイル転送 ID |
| execution_id | string(UUID) | バッチ実行要求 UUID |
| category | string | ファイルカテゴリ |
| file_name | string | ファイル名 |
| content_type | string | Content-Type |
| file_size | number | ファイルサイズ |
| checksum_sha256 | string | SHA-256 |
| transfer_status | string | 転送状態 |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| Agent が対象外 | 403 | 対象実行要求に紐づかない Agent からの要求 |
| 実行要求が存在しない | 404 | 指定 UUID の実行要求がない |
| 未対応カテゴリ | 400 | `log`、`artifact` 以外が指定された |
| ファイル保存失敗 | 500 | ストレージへの保存に失敗 |
