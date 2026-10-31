# バッチスケジュール登録・更新 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0020 |
| 分類 | バッチスケジュールマスタ |
| メソッド | POST |
| パス | `/api/master/batch/schedule/register` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

バッチスケジュールを登録または更新し、登録後の詳細情報を返す。

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| id | number | 任意 | 更新対象ID |
| batch_definition_id | number | 必須 | バッチ定義ID |
| target_agent_id | number | 必須 | 対象エージェントID |
| cron_expression | string | 必須 | Cron式 |
| start_date | string | 任意 | 開始日 |
| end_date | string | 任意 | 終了日 |
| allow_holiday_execution | boolean | 必須 | 休日実行可否 |
| enabled | boolean | 必須 | 有効状態 |
| priority | number | 必須 | 優先度 |

## レスポンス

バッチスケジュール一覧取得APIの1要素と同じ。

