# バッチスケジュール管理

## 概要

バッチ定義に対し、対象エージェント、Cron式、実行期間、優先度を設定して実行スケジュールを管理する。

## 対象API

| No | API | 概要 |
| --- | --- | --- |
| 1 | `/api/master/batch/schedule/list` | バッチスケジュール一覧取得 |
| 2 | `/api/master/batch/schedule/detail` | バッチスケジュール詳細取得 |
| 3 | `/api/master/batch/schedule/register` | バッチスケジュール登録・更新 |

## 登録・更新

- IDが未指定または0以下の場合は新規登録する。
- IDが指定されている場合は既存レコードを更新する。
- 更新対象が存在しない場合はエラーとする。
- バッチ定義ID、対象エージェントID、Cron式、休日実行可否、有効状態、優先度は必須とする。

## 主な項目

| 項目 | 内容 |
| --- | --- |
| batch_definition_id | 紐づくバッチ定義ID |
| target_agent_id | 実行対象エージェントID |
| cron_expression | 実行スケジュールを表すCron式 |
| start_date | 実行開始日 |
| end_date | 実行終了日 |
| allow_holiday_execution | 休日実行可否 |
| enabled | 有効状態 |
| priority | 優先度 |

