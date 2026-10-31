# Agent バッチ実行ファイル一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0026 |
| 分類 | Agent バッチ実行ファイル |
| メソッド | GET |
| パス | `/api/v1/agent/batch/executions/{executionId}/files` |
| 認証 | Agent専用Bearer |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent が対象の実行要求に紐づくファイル一覧を取得する。
戻り値は `BatchExecutionFileDetailDto` の配列で、download API の `fileId` と SHA-256 検証値を含む。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| executionId | string(UUID) | 必須 | バッチ実行要求 UUID |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | ファイル ID |
| file_name | string | ファイル名 |
| content_type | string | Content-Type |
| file_size | number | ファイルサイズ |
| sha256 | string | SHA-256 ハッシュ |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| 実行要求が存在しない、または対象端末・登録世代が異なる | 404 | 要求の存在を他端末へ公開しない |

認証・端末単位認可は[Agent専用認証API](0041_agent_registration_authentication.md)を参照する。

