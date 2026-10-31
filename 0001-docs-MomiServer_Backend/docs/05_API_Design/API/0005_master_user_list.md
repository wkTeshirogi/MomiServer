# ユーザー一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0005 |
| 分類 | ユーザーマスタ |
| メソッド | POST |
| パス | `/api/master/user/list` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

登録済みユーザーの一覧を取得する。

## 処理概要

ユーザーをID昇順で全件取得し、レスポンス配列へ変換する。対象データが0件の場合は空配列を返却する。パスワードはレスポンスに含めない。

## リクエスト

リクエストパラメータなし。

## レスポンス

JSON配列で返却する。

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
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 管理者権限なし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |
