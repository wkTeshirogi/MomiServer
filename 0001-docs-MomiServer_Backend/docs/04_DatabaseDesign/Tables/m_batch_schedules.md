# m_batch_schedules

## 概要

バッチ定義に対する実行スケジュールを管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | バッチスケジュールID |
| 2 | batch_definition_id | BIGINT | 不可 | なし | 外部キー | バッチ定義ID |
| 3 | target_agent_id | BIGINT | 不可 | なし | なし | 対象エージェントID |
| 4 | cron_expression | VARCHAR(255) | 不可 | なし | なし | Cron式 |
| 5 | start_date | DATE | 可 | なし | 期間整合性 | 開始日 |
| 6 | end_date | DATE | 可 | なし | 期間整合性 | 終了日 |
| 7 | allow_holiday_execution | BOOLEAN | 不可 | false | なし | 休日実行可否 |
| 8 | enabled | BOOLEAN | 不可 | true | なし | 有効状態 |
| 9 | priority | INTEGER | 不可 | 5 | 1から9 | 優先度 |
| 10 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 11 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_m_batch_schedules_definition_id | batch_definition_id | バッチ定義単位の検索 |
| 2 | idx_m_batch_schedules_target_agent_id | target_agent_id | エージェント単位の検索 |
| 3 | idx_m_batch_schedules_enabled | enabled | 有効状態検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## マスタ削除制御

バッチスケジュールを通常利用から除外する場合は、物理削除ではなくマスタステータスを削除扱いの状態へ更新する。
削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。
`enabled` は有効 / 無効の切り替えに限定し、削除状態の管理には使用しない。
