# スケジュール実行状況取得API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0043 |
| 分類 | バッチスケジュール |
| メソッド | GET |
| パス | `/api/master/batch/schedule/executions` |
| 認証 | 管理ユーザーJWT |
| 権限 | 管理者（AdminOnly） |
| 正常ステータス | HTTP 200 |

## 概要

生成済み実行要求の件数を、予定日時に基づいて集計する。未生成の予定を返すAPIではない。

## 処理概要

1. 期間を検証する。終了日が開始日より前、または開始日の30日後を超える場合は拒否する。
2. schedule_idがNULLでない要求を開始日0時以上、終了日の翌日0時未満で抽出する。
3. スケジュール・予定日・時間帯・状態別に集計する。返却順序は保証しない。

## クエリパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| from | date | 必須 | yyyy-MM-dd | 開始日 |
| to | date | 必須 | from以上、from+30日以下 | 終了日。両端を含め最大31日 |

## レスポンス

以下の要素の配列。対象なしは空配列。

| 項目 | 型 | 説明 |
| --- | --- | --- |
| schedule_id | number | スケジュールID |
| scheduled_date | string(date) | 予定日 |
| scheduled_hour | number | 予定時間帯（0～23） |
| execution_status | string | 保存済みの実行状態 |
| execution_count | number | 該当件数 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 期間の前後・上限違反 |
| 401 | UNAUTHORIZED | 未認証 |
| 403 | ACCESS_DENIED | 管理者権限なし |

## 備考

日付・時間帯はDBのscheduled_fire_timeから導出する。実行開始日や終了日で集計しない。
根拠: BatchScheduleMasterController、BatchScheduleService、BatchScheduleExecutionRepository。
