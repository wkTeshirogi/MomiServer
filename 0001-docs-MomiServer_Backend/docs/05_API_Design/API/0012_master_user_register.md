# ユーザー登録・更新 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0012 |
| 分類 | ユーザーマスタ |
| メソッド | POST |
| パス | `/api/master/user/register` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

ユーザーを登録または更新し、認証トークンを返す。

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| email | string | 必須 | メールアドレス |
| password | string | 新規登録時必須 | パスワード |
| role | string | 必須 | `ADMIN_ROLE`, `USER_ROLE`, `BAN_USER_ROLE` |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| accessToken | string | アクセストークン |
| refreshToken | string | リフレッシュトークン |
| expiresnIn | number | アクセストークン有効期限秒数 |

