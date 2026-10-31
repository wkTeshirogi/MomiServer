# t_vehicles

## 概要

車検期限管理の対象となる車両識別情報と、現在の車検有効期間満了日を管理する。
所有者・使用者の氏名および住所、取り込んだ車検証原本は保持しない。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | 車両ID |
| 2 | display_name | VARCHAR(100) | 不可 | なし | なし | 画面上の識別名 |
| 3 | registration_number | VARCHAR(50) | 不可 | なし | なし | 自動車登録番号または車両番号 |
| 4 | chassis_number | VARCHAR(100) | 不可 | なし | 管理中の車両で一意 | 車台番号 |
| 5 | vehicle_identification_code | VARCHAR(100) | 可 | なし | 値がある管理中の車両で一意 | 電子車検証の車両識別符号 |
| 6 | vehicle_name | VARCHAR(100) | 不可 | なし | なし | 車名 |
| 7 | model_code | VARCHAR(100) | 可 | なし | なし | 型式 |
| 8 | first_registration_month | VARCHAR(7) | 可 | なし | `YYYY-MM`形式 | 初度登録年月または初度検査年月 |
| 9 | inspection_expiry_date | DATE | 不可 | なし | なし | 車検有効期間満了日 |
| 10 | issued_date | DATE | 可 | なし | なし | 車検証の交付年月日 |
| 11 | usage_category | VARCHAR(50) | 可 | なし | なし | 自家用・事業用等の区分を英語値で保存 |
| 12 | purpose | VARCHAR(50) | 可 | なし | なし | 乗用・貨物等の用途を英語値で保存 |
| 13 | status | VARCHAR(20) | 不可 | VALID | CHECK | 車検期限または管理終了状態 |
| 14 | notes | TEXT | 可 | なし | なし | 備考 |
| 15 | created_by | VARCHAR(255) | 不可 | なし | なし | 登録者メールアドレス |
| 16 | updated_by | VARCHAR(255) | 不可 | なし | なし | 最終更新者メールアドレス |
| 17 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 18 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 状態値

| 値 | 内容 |
| --- | --- |
| VALID | 満了日まで31日以上 |
| DUE_SOON | 満了日まで30日以内 |
| URGENT | 満了日まで7日以内 |
| EXPIRED | 満了日を経過 |
| UNKNOWN | 満了日を判定できない |
| ARCHIVED | 管理対象外 |

一覧・詳細APIでは現在日付から状態と残日数を再計算する。DBの`status`は保存時点の状態と管理終了状態を保持する。

## インデックス

| No | インデックス名 | 対象 | 用途 |
| --- | --- | --- | --- |
| 1 | uk_t_vehicles_active_chassis_number | chassis_number、statusがARCHIVED以外 | 管理中車両の車台番号重複防止 |
| 2 | uk_t_vehicles_active_identification_code | vehicle_identification_code、値あり、statusがARCHIVED以外 | 車両識別符号の重複防止 |
| 3 | idx_t_vehicles_expiry_date | inspection_expiry_date | 満了日順検索 |
| 4 | idx_t_vehicles_status | status | 状態検索 |

## 更新制御

更新トリガーにより`updated_dt`を現在日時へ更新する。管理終了は物理削除せず`ARCHIVED`へ変更する。
