# Agent ファイルダウンロード API

> 状態: 旧設計案・未実装。このパスは現行APIとして提供していない。以下の入出力・例外は実装済みの契約ではない。
> 実装を再検討する場合はAgent専用Bearerと対象ファイルの端末・登録世代の認可を前提とする。

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0032 |
| 分類 | Agent ファイル転送 |
| メソッド | GET |
| パス | `/api/v1/agent/download/{downloadUuid}` |
| 認証 | 旧案: Gateway APIキー。現行方式では不可、再設計が必要 |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent が事前連携されたダウンロード UUID を使用してファイルを取得する。
UUID はダウンロード完了通知を受けた後に消費済みとなり、次回用 UUID が再生成される。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| downloadUuid | string(UUID) | 必須 | ダウンロード用の使い切り UUID |

## レスポンス

| 項目 | 内容 |
| --- | --- |
| Content-Type | 登録済み `content_type`。未設定時は `application/octet-stream` |
| Content-Disposition | `attachment; filename="{file_name}"` |
| Body | ファイル本体 |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| Agent が対象外 | 403 | 対象ファイルに紐づかない Agent からの要求 |
| UUID が存在しない | 404 | 指定 UUID のファイルが存在しない |
| UUID が消費済み | 404 | 既に完了通知済みの UUID |
| UUID が期限切れ | 410 | 有効期限を超過した UUID |
| 実ファイルが存在しない | 404 | ストレージ上のファイルが存在しない |
