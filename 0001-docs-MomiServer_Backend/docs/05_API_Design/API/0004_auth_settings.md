# ユーザー設定変更 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0004 |
| 分類 | 認証 |
| メソッド | POST |
| パス | `/api/auth/settings` |
| 認証 | 必須 |
| 権限 | ADMIN |
| 正常ステータス | HTTP 200 |

## 概要

指定されたユーザーのパスワードと権限を更新する。パスワードが未指定または空文字の場合、既存パスワードは維持する。

## 処理概要

1. ユーザーJWTを検証し、ADMIN権限を持つことを確認する。
2. リクエストボディの入力値を検証する。
3. 指定されたユーザーのパスワードと権限を更新する。

## リクエストボディ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| email | string | 必須 | メール形式 | 対象ユーザーのメールアドレス |
| password | string | 任意 | なし | 変更後パスワード |
| role | string | 必須 | `ADMIN_ROLE`, `USER_ROLE`, `BAN_USER_ROLE` | 権限 |

## レスポンス

レスポンスボディなし。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 入力値の形式不正 |
| 400 | `MALFORMED_REQUEST` | リクエストボディ読み取り不可 |
| 401 | `UNAUTHORIZED` | 未認証またはトークン不正 |
| 403 | `ACCESS_DENIED` | ADMIN権限がない |
| 404 | `RESOURCE_NOT_FOUND` | 対象ユーザーなし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |

