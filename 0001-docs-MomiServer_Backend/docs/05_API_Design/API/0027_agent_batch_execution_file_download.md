# Agent バッチ実行ファイルダウンロード API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0027 |
| 分類 | Agent バッチ実行ファイル |
| メソッド | GET |
| パス | `/api/v1/agent/batch/executions/{executionId}/files/{fileId}/download` |
| 認証 | Agent専用Bearer |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent が対象の実行要求に紐づくファイルをダウンロードする。
レスポンスはファイル本体であり、`Content-Disposition` に登録済み `file_name` を設定する。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| executionId | string(UUID) | 必須 | バッチ実行要求 UUID |
| fileId | number | 必須 | ファイル ID |

## レスポンス

| 項目 | 内容 |
| --- | --- |
| Content-Type | 登録済み `content_type`。未設定時は `application/octet-stream` |
| Content-Disposition | `attachment; filename="{file_name}"` |
| Body | ファイル本体 |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| 実行要求が存在しない | 404 | 指定 UUID の実行要求がない |
| ファイルが存在しない | 404 | 指定 ID のファイルが実行要求に紐づかない、または実ファイルが存在しない |

