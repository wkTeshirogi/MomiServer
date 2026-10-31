# サービス一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0009 |
| 分類 | サービスマスタ |
| メソッド | POST |
| パス | `/api/master/service/list` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

登録済みサービスの一覧を取得する。

## 処理概要

サービスをID昇順で全件取得し、レスポンス配列へ変換する。対象データが0件の場合は空配列を返却する。ページング、絞り込み、最大取得件数はAPI仕様として定義しない。

## リクエスト

リクエストパラメータなし。

## レスポンス

JSON配列で返却する。

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

## 注意事項

サービスレスポンスにはログ、操作許可、自動再起動、通知レベル、説明に関する項目を含める。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 管理者権限なし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |
