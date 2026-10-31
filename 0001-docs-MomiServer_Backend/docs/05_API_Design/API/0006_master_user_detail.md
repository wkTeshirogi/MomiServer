# ユーザー詳細取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0006 |
| 分類 | ユーザーマスタ |
| メソッド | POST |
| パス | `/api/master/user/detail` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

指定ユーザーの詳細情報を取得する。

## 処理概要

指定されたユーザーIDに一致するユーザーを1件取得し、レスポンスへ変換する。対象データが存在しない場合は404を返却する。パスワードはレスポンスに含めない。

## リクエストパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| id | number | 必須 | ユーザーID。必須・範囲制約はAPIレベルでは明示しない |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | ユーザーID |
| email | string | メールアドレス |
| role | string | 権限 |
| createdDt | string | 作成日時 |
| updatedDt | string | 更新日時 |
| lastLoginAt | string | 最終ログイン日時 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | ユーザーID不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 管理者権限なし |
| 404 | `RESOURCE_NOT_FOUND` | 対象ユーザーなし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |
