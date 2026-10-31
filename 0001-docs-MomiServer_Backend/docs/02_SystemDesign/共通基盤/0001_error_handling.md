# 共通エラー制御

## 概要

APIで発生した例外を共通のJSONレスポンスへ変換する。

## エラー応答形式

| 項目 | 内容 |
| --- | --- |
| timestamp | エラー発生日時 |
| status | HTTPステータス |
| error | HTTP理由句 |
| code | アプリケーションエラーコード |
| message | 表示可能なメッセージ |
| path | リクエストパス |
| fieldErrors | 入力項目ごとのエラー |

## 主なエラー

| code | HTTP | 内容 |
| --- | --- | --- |
| VALIDATION_ERROR | 400 | 入力値不正 |
| INVALID_PARAMETER | 400 | パラメータ不正 |
| MALFORMED_REQUEST | 400 | リクエストボディ不正 |
| AUTHENTICATION_FAILED | 401 | 認証失敗 |
| INVALID_TOKEN | 401 | トークン不正 |
| ACCESS_DENIED | 403 | 権限不足 |
| RESOURCE_NOT_FOUND | 404 | 対象データなし |
| DUPLICATE_EMAIL | 409 | メールアドレス重複 |
| DATABASE_ERROR | 500 | DB処理エラー |
| INTERNAL_SERVER_ERROR | 500 | 予期しないエラー |
