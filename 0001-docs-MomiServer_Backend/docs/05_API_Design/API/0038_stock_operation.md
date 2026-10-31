# 実在庫数量操作 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0038 |
| 分類 | 在庫管理 |
| メソッド | POST |
| パス | `/api/stock/operation` |
| 認証 | 必須 |
| 権限 | 認証済み |
| 正常ステータス | HTTP 200 |

## 概要

入庫、出庫、棚卸、破棄、削除の操作として実在庫数量と在庫ステータスを更新し、操作履歴を保存する。

## 処理概要

1. 認証済みユーザーであることを確認する。
2. リクエストボディの入力値を検証する。
3. 対象の実在庫レコードを排他ロック付きで取得する。
4. 品目マスタの `min_quantity` を取得し、操作後数量から在庫ステータスを算出する。
5. 実在庫の数量とステータスを更新する。
6. `t_inventory_stock_histories` に数量差分、変更前後数量、変更前後ステータスを保存する。
7. 更新後の実在庫情報を返却する。

## パスパラメータ

なし。

## クエリパラメータ

なし。

## リクエストボディ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| inventory_stock_id | number | 必須 | 既存の実在庫 ID | 操作対象の実在庫 ID |
| change_type | string | 必須 | DB制約値 | `inbound`、`outbound`、`stocktake`、`discard`、`delete` |
| quantity | number | 必須 | 0以上 | 操作数量。`stocktake` では変更後数量として扱う |
| reason | string | 任意 | 255文字以内 | 変更理由 |
| memo | string | 任意 | なし | メモ |

## 操作種別

| change_type | 数量計算 | ステータス計算 |
| --- | --- | --- |
| inbound | 変更前数量 + 操作数量 | 操作後数量と品目マスタの `min_quantity` から算出 |
| outbound | 変更前数量 - 操作数量 | 操作後数量と品目マスタの `min_quantity` から算出 |
| stocktake | 操作数量を変更後数量として設定 | 操作後数量と品目マスタの `min_quantity` から算出 |
| discard | 変更前数量 - 操作数量 | 操作後数量と品目マスタの `min_quantity` から算出 |
| delete | 数量は変更しない | `deleted` を設定 |

## レスポンス

更新後の `ItemStockDetailDto` を返却する。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 必須項目不足、数量不正、変更理由文字数超過 |
| 400 | `INVALID_PARAMETER` | 未対応の変更種別、または出庫・破棄で数量がマイナスになる操作 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 500 | `INTERNAL_SERVER_ERROR` | 対象実在庫が存在しない、または予期しないエラー |

## 備考

- `quantity_after` が 0 の場合は `out_of_stock` とする。
- `quantity_after` が 1 以上かつ品目マスタの `min_quantity` 以下の場合は `low` とする。
- `quantity_after` が品目マスタの `min_quantity` を上回る場合は `available` とする。
