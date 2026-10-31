# t_inventory_stocks

## 概要

品目ごとの実在庫を管理する。

同じ品目を複数の保管場所で管理できるように、品目マスタとは分離して現在数量、補充推奨数量、保管場所、在庫ステータスを保持する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | 実在庫ID |
| 2 | inventory_item_id | BIGINT | 不可 | なし | 外部キー | 品目ID |
| 3 | storage_location | VARCHAR(255) | 不可 | なし | なし | 保管場所 |
| 4 | quantity | INTEGER | 不可 | 0 | 0以上 | 現在数量 |
| 5 | replenish_quantity | INTEGER | 可 | なし | 0以上 | 補充時の推奨数量 |
| 6 | inventory_status | VARCHAR(50) | 不可 | available | CHECK | 在庫ステータス |
| 7 | expiration_date | DATE | 可 | なし | なし | 期限日 |
| 8 | memo | TEXT | 可 | なし | なし | メモ |
| 9 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 10 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 在庫ステータス

| 値 | 内容 |
| --- | --- |
| available | 現在数量が品目マスタの標準最低数量を上回る状態。画面では「在庫あり」と表示する。 |
| low | 現在数量が1以上かつ品目マスタの標準最低数量以下の状態。画面では「在庫注意」と表示する。 |
| out_of_stock | 現在数量が0の状態。画面では「在庫なし」と表示する。 |
| deleted | 一覧や通常利用から除外する論理削除状態。画面では「削除」と表示する。 |

## ステータス更新方針

| 条件 | ステータス |
| --- | --- |
| 現在数量が0 | out_of_stock |
| 現在数量が1以上かつ品目マスタの標準最低数量以下 | low |
| 現在数量が品目マスタの標準最低数量を上回る | available |
| 削除操作を行った場合 | deleted |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_inventory_stocks_item_id | inventory_item_id | 品目単位の実在庫検索 |
| 2 | idx_t_inventory_stocks_status | inventory_status | 在庫ステータス別検索 |
| 3 | idx_t_inventory_stocks_storage_location | storage_location | 保管場所別検索 |
| 4 | idx_t_inventory_stocks_updated_dt | updated_dt | 更新日時順検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## 備考

実在庫では有効状態を表す `enabled` は保持しない。

通常利用から除外する場合は、在庫ステータスを `deleted` に更新する。

在庫注意判定に使用する最低数量は `m_inventory_items.min_quantity` を参照する。
