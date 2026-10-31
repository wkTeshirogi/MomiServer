# t_vehicle_inspection_histories

## 概要

車両の初回登録、車検有効期間満了日の更新、管理終了を履歴として記録する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | 車検期限変更履歴ID |
| 2 | vehicle_id | BIGINT | 不可 | なし | t_vehiclesへの外部キー | 車両ID |
| 3 | change_type | VARCHAR(30) | 不可 | なし | CHECK | 変更種別 |
| 4 | previous_expiry_date | DATE | 可 | なし | なし | 変更前の満了日 |
| 5 | new_expiry_date | DATE | 可 | なし | なし | 変更後の満了日 |
| 6 | changed_by | VARCHAR(255) | 不可 | なし | なし | 変更者メールアドレス |
| 7 | changed_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 変更日時 |

## 変更種別

| 値 | 内容 |
| --- | --- |
| CREATED | 車両の初回登録 |
| EXPIRY_UPDATED | 車検有効期間満了日の変更 |
| ARCHIVED | 車両の管理終了 |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_vehicle_inspection_histories_vehicle_id | vehicle_id、changed_dt降順 | 車両単位の履歴表示 |
