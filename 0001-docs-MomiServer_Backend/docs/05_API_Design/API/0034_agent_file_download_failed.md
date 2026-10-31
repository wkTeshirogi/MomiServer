# Agent ファイルダウンロード失敗通知 API

> 状態: 旧設計案・未実装。このパスは現行APIとして提供していない。以下の入出力・例外は実装済みの契約ではない。
> 実装を再検討する場合はAgent専用Bearerと対象ファイルの端末・登録世代の認可を前提とする。

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0034 |
| 分類 | Agent ファイル転送 |
| メソッド | POST |
| パス | `/api/v1/agent/download/{downloadUuid}/failed` |
| 認証 | 旧案: Gateway APIキー。現行方式では不可、再設計が必要 |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent がファイル取得、保存、検証のいずれかに失敗したことを通知する。
Server は失敗理由を記録し、必要に応じて再取得用 UUID を再生成できる状態にする。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| downloadUuid | string(UUID) | 必須 | ダウンロード用の使い切り UUID |

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| reason | string | 必須 | 失敗理由 |
| failed_at | string(datetime) | 任意 | Agent 側の失敗検知日時 |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| file_id | number | ファイル転送 ID |
| transfer_status | string | `failed` |
| retryable | boolean | 再取得可能か |
| next_download_uuid | string(UUID) | 再生成された UUID。再取得しない場合は null |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| Agent が対象外 | 403 | 対象ファイルに紐づかない Agent からの要求 |
| UUID が存在しない | 404 | 指定 UUID のファイルが存在しない |
| UUID が消費済み | 409 | 既に完了通知済み |
