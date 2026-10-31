# サービス登録・更新 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0014 |
| 分類 | サービスマスタ |
| メソッド | POST |
| パス | `/api/master/service/register` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

監視対象サービスを登録または更新し、登録後の詳細情報を返す。

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| id | number | 任意 | 更新対象ID |
| service_name | string | 必須 | サービス名 |
| view_name | string | 必須 | 表示名 |
| target_machine | number | 必須 | 対象マシンID |
| status | string | 必須 | 状態 |
| log_path | string | 必須 | ログパス |
| use_journalctl | boolean | 任意 | journalctl利用有無 |
| allow_start | boolean | 任意 | 起動許可 |
| allow_stop | boolean | 任意 | 停止許可 |
| allow_restart | boolean | 任意 | 再起動許可 |
| auto_restart | boolean | 任意 | 自動再起動有無 |
| notice_level | string | 任意 | 通知レベル |
| description | string | 任意 | 説明 |

## レスポンス

サービス詳細取得APIと同じ。

