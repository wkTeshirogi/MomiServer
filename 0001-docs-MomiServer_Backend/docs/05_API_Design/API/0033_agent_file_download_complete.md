# Agent ファイルダウンロード完了通知 API

> 状態: 旧設計案・未実装。このパスは現行APIとして提供していない。以下の入出力・例外は実装済みの契約ではない。
> 実装を再検討する場合はAgent専用Bearerと対象ファイルの端末・登録世代の認可を前提とする。

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0033 |
| 分類 | Agent ファイル転送 |
| メソッド | POST |
| パス | `/api/v1/agent/download/{downloadUuid}/complete` |
| 認証 | 旧案: Gateway APIキー。現行方式では不可、再設計が必要 |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent がファイルの取得と SHA-256 検証に成功したことを通知する。
Server は通知を受けた時点で対象 UUID を消費済みにし、次回ダウンロード用 UUID を再生成する。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| downloadUuid | string(UUID) | 必須 | ダウンロード用の使い切り UUID |

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| checksum_sha256 | string | 任意 | Agent 側で検証した SHA-256 |
| downloaded_at | string(datetime) | 任意 | Agent 側のダウンロード完了日時 |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| file_id | number | ファイル転送 ID |
| transfer_status | string | `consumed` |
| next_download_uuid | string(UUID) | 再生成された次回用 UUID |
| regenerated_at | string(datetime) | UUID 再生成日時 |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| Agent が対象外 | 403 | 対象ファイルに紐づかない Agent からの要求 |
| UUID が存在しない | 404 | 指定 UUID のファイルが存在しない |
| UUID が消費済み | 409 | 既に完了通知済み |
| ハッシュ不一致 | 409 | 登録済み SHA-256 と通知値が一致しない |
