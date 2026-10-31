# サービスマスタ参照

## 概要

管理者向けにサービス一覧とサービス詳細を参照する機能を提供する。

## 対象API

| No | API | 概要 |
| --- | --- | --- |
| 1 | `/api/master/service/list` | サービス一覧取得 |
| 2 | `/api/master/service/detail` | サービス詳細取得 |

## 一覧取得

- サービスをID昇順で取得する。
- 0件の場合は空配列を返却する。
- ページング、絞り込み、最大取得件数は定義しない。

## 詳細取得

- サービスIDを指定して1件取得する。
- 対象サービスが存在しない場合は404を返却する。

## 主な項目

| 項目 | 内容 |
| --- | --- |
| service_name | サービス名 |
| view_name | 表示名 |
| target_machine | 対象マシンID |
| status | 状態 |
| log_path | ログパス |
| allow_start | 起動許可 |
| allow_stop | 停止許可 |
| allow_restart | 再起動許可 |
| auto_restart | 自動再起動有無 |
| notice_level | 通知レベル |
