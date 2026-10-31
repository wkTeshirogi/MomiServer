# サービス詳細取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0010 |
| 分類 | サービスマスタ |
| メソッド | POST |
| パス | `/api/master/service/detail` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

指定サービスの詳細情報を取得する。

## 処理概要

指定されたサービスIDに一致するサービスを1件取得し、レスポンスへ変換する。対象データが存在しない場合は404を返却する。

## リクエストパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| id | number | 必須 | サービスID。必須・範囲制約はAPIレベルでは明示しない |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | サービスID |
| service_name | string | サービス名 |
| view_name | string | 表示名 |
| target_machine | number | 対象マシンID |
| status | string | 状態 |
| createdAt | string | 作成日時 |
| updatedAt | string | 更新日時 |
| log_path | string | ログパス |
| use_journalctl | boolean | journalctl利用有無 |
| allow_start | boolean | 起動許可 |
| allow_stop | boolean | 停止許可 |
| allow_restart | boolean | 再起動許可 |
| auto_restart | boolean | 自動再起動有無 |
| notice_level | string | 通知レベル |
| description | string | 説明 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | サービスID不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 管理者権限なし |
| 404 | `RESOURCE_NOT_FOUND` | 対象サービスなし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |
