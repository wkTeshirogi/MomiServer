# m_batch_definition_parameters

## 概要

バッチ定義に紐づく実行パラメータを管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | パラメータID |
| 2 | batch_definition_id | BIGINT | 不可 | なし | 外部キー | バッチ定義ID |
| 3 | parameter_name | VARCHAR(255) | 不可 | なし | 定義内一意 | パラメータ名 |
| 4 | parameter_type | VARCHAR(50) | 不可 | なし | なし | パラメータ種別 |
| 5 | fixed_value | TEXT | 可 | なし | 固定値時必須 | 固定値 |
| 6 | dynamic_value_type | VARCHAR(50) | 可 | なし | 動的値時必須 | 動的値種別 |
| 7 | value_format | VARCHAR(100) | 可 | なし | なし | 値フォーマット |
| 8 | display_order | INTEGER | 不可 | なし | 0以上 | 表示順 |
| 9 | enabled | BOOLEAN | 不可 | true | なし | 有効状態 |
| 10 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 11 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_m_batch_definition_parameters_definition_id | batch_definition_id | バッチ定義単位の検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## マスタ削除制御

バッチ定義パラメータを通常利用から除外する場合は、物理削除ではなくマスタステータスを削除扱いの状態へ更新する。
削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。
`enabled` は有効 / 無効の切り替えに限定し、削除状態の管理には使用しない。
