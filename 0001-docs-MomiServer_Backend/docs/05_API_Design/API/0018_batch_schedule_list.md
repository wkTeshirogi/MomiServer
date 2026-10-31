# バッチスケジュール一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0018 |
| 分類 | バッチスケジュールマスタ |
| メソッド | POST |
| パス | `/api/master/batch/schedule/list` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

登録済みのバッチスケジュール一覧を取得する。

## リクエスト

リクエストボディなし。

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | バッチスケジュールID |
| batch_definition_id | number | バッチ定義ID |
| target_agent_id | number | 対象エージェントID |
| cron_expression | string | Cron式 |
| start_date | string | 開始日 |
| end_date | string | 終了日 |
| allow_holiday_execution | boolean | 休日実行可否 |
| enabled | boolean | 有効状態 |
| priority | number | 優先度 |
| createdAt | string | 作成日時 |
| updatedAt | string | 更新日時 |

