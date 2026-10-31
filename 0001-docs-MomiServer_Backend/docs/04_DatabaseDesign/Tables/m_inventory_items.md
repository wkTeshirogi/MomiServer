# m_inventory_items

## 概要

一定数を保持しておきたい備品・消耗品の品目情報を管理する。

このテーブルでは品目そのものの情報、品目単位の標準最低数量、品目管理ステータスを管理し、現在数量、保管場所、在庫ステータス、期限日などの実在庫情報は `t_inventory_stocks` で管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | 品目ID |
| 2 | item_name | VARCHAR(255) | 不可 | なし | なし | 品目名 |
| 3 | category | VARCHAR(100) | 不可 | なし | なし | カテゴリ |
| 4 | unit | VARCHAR(50) | 不可 | なし | なし | 単位 |
| 5 | min_quantity | INTEGER | 不可 | 0 | 0以上 | 品目単位の標準最低数量 |
| 6 | manufacturer | VARCHAR(255) | 可 | なし | なし | メーカー |
| 7 | model_number | VARCHAR(255) | 可 | なし | なし | 型番 |
| 8 | jan_code | VARCHAR(20) | 可 | なし | なし | JANコード |
| 9 | purchase_url | TEXT | 可 | なし | なし | 購入URL |
| 10 | status | VARCHAR(50) | 不可 | active | CHECK | 品目管理ステータス |
| 11 | memo | TEXT | 可 | なし | なし | メモ |
| 12 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 13 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 品目管理ステータス

| 値 | 内容 |
| --- | --- |
| active | 通常利用できる品目。画面では「有効」と表示する。 |
| disabled | 一時的に利用しない品目。画面では「無効」と表示する。 |
| deleted | 一覧や通常利用から除外する論理削除状態。画面では「削除」と表示する。 |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_m_inventory_items_category | category | カテゴリ別検索 |
| 2 | idx_m_inventory_items_item_name | item_name | 品目名検索 |
| 3 | idx_m_inventory_items_jan_code | jan_code | JANコード検索 |
| 4 | idx_m_inventory_items_status | status | 品目管理ステータス別検索 |
| 5 | idx_m_inventory_items_updated_dt | updated_dt | 更新日時順検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## 備考

品目マスタでは現在数量、保管場所、在庫ステータス、期限日を保持しない。

`min_quantity` は品目単位の標準最低数量として扱う。保管場所や実在庫単位で最低数量を個別に管理する場合は、`t_inventory_stocks.minimum_quantity` を使用する。

品目を通常利用から除外する場合は、品目管理ステータスを `deleted` に更新する。

品目削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。

在庫数量に基づく `在庫あり`、`在庫なし`、`在庫注意`、`削除` は `t_inventory_stocks.inventory_status` で管理する。
