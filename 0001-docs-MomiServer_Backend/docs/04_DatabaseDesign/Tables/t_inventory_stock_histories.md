# t_inventory_stock_histories

## 概要

実在庫の数量変更履歴を管理する。

入庫、出庫、棚卸、破棄、削除などの操作を履歴として記録し、在庫数量の変化を追跡できるようにする。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | 在庫変動履歴ID |
| 2 | inventory_stock_id | BIGINT | 不可 | なし | 外部キー | 実在庫ID |
| 3 | change_type | VARCHAR(50) | 不可 | なし | CHECK | 変更種別 |
| 4 | quantity_delta | INTEGER | 不可 | 0 | なし | 増減数量 |
| 5 | quantity_before | INTEGER | 不可 | 0 | 0以上 | 変更前数量 |
| 6 | quantity_after | INTEGER | 不可 | 0 | 0以上 | 変更後数量 |
| 7 | inventory_status_before | VARCHAR(50) | 可 | なし | なし | 変更前在庫ステータス |
| 8 | inventory_status_after | VARCHAR(50) | 可 | なし | なし | 変更後在庫ステータス |
| 9 | reason | VARCHAR(255) | 可 | なし | なし | 変更理由 |
| 10 | memo | TEXT | 可 | なし | なし | メモ |
| 11 | changed_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 変更日時 |
| 12 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |

## 変更種別

| 値 | 内容 |
| --- | --- |
| inbound | 在庫数量を増加させる。画面・帳票では「入庫」と表示する。 |
| outbound | 在庫数量を減少させる。画面・帳票では「出庫」と表示する。 |
| stocktake | 実数確認により数量を絶対値で調整する。画面・帳票では「棚卸」と表示する。 |
| discard | 期限切れや故障などで在庫数量を減少させる。画面・帳票では「破棄」と表示する。 |
| delete | 実在庫を通常利用から除外する。画面・帳票では「削除」と表示する。 |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_inventory_stock_histories_stock_id | inventory_stock_id | 実在庫単位の履歴検索 |
| 2 | idx_t_inventory_stock_histories_change_type | change_type | 変更種別検索 |
| 3 | idx_t_inventory_stock_histories_changed_dt | changed_dt | 変更日時順検索 |

## 備考

数量変更を伴わないステータス変更の場合、`quantity_delta` は0とする。
