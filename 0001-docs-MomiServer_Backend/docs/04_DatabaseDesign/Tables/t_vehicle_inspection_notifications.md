# t_vehicle_inspection_notifications

## 概要

車両・車検満了日・通知段階ごとの通知済み記録を保持する。V36で作成する。
通知本体の保持削除後も、同じ車検期限について重複通知しないために記録を残す。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | vehicle_id | BIGINT | 不可 | なし | 複合主キー・外部キー | 車両ID |
| 2 | inspection_expiry_date | DATE | 不可 | なし | 複合主キー | 通知対象の満了日 |
| 3 | notification_stage | VARCHAR(30) | 不可 | なし | 複合主キー・CHECK | 通知段階 |
| 4 | notification_id | UUID | 可 | なし | 外部キー | 通知本体の外部参照ID |
| 5 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 通知枠作成日時 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_vehicle_inspection_notifications_pkey | vehicle_id、inspection_expiry_date、notification_stage | はい | 通知重複防止 |

## 外部キー

| No | カラム | 参照先 | 削除時 | 更新時 |
| --- | --- | --- | --- | --- |
| 1 | vehicle_id | t_vehicles.id | CASCADE | NO ACTION |
| 2 | notification_id | t_notifications.notification_id | SET NULL | NO ACTION |

## 更新制御

通知枠を一意制約で確保し、通知登録と同一トランザクションでnotification_idを関連付ける。
通知作成が失敗した場合は確保をロールバックし、次回の定期処理で再試行する。
通知本体が削除された場合はnotification_idだけをNULLにして、重複防止記録を保持する。
車両の管理終了はt_vehicles.statusで判断し、通知済み記録は削除しない。

## 備考

- 段階は`TWO_MONTHS`、`ONE_MONTH`、`SEVEN_DAYS`、`DUE_TODAY`、`EXPIRED`。V39で当日段階を追加する。
- 判定と配信仕様は[車検期限管理 基本設計](../../02_SystemDesign/車両管理/0001_vehicle_inspection_management.md)を参照する。
