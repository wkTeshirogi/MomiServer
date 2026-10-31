# 実在庫履歴取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0039 |
| 分類 | 在庫管理 |
| メソッド | POST |
| パス | `/api/stock/history` |
| 認証 | 必須 |
| 権限 | 認証済み |
| 正常ステータス | HTTP 200 |

## 概要

指定した実在庫 ID に紐づく数量変更履歴を取得する。
入庫、出庫、棚卸、破棄、削除などの操作履歴を確認するために使用する。

## 処理概要

1. 認証済みユーザーであることを確認する。
2. クエリパラメータ `inventoryStockId` に一致する履歴を取得する。
3. `changed_dt` 降順、`id` 降順で並べて返却する。

## パスパラメータ

なし。

## クエリパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| inventoryStockId | number | 必須 | 1以上 | 履歴取得対象の実在庫 ID |

## リクエストボディ

なし。

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | 在庫変動履歴 ID |
| inventory_stock_id | number | 実在庫 ID |
| change_type | string | `inbound`、`outbound`、`stocktake`、`discard`、`delete` |
| quantity_delta | number | 増減数量 |
| quantity_before | number | 変更前数量 |
| quantity_after | number | 変更後数量 |
| inventory_status_before | string | 変更前在庫ステータス |
| inventory_status_after | string | 変更後在庫ステータス |
| reason | string | 変更理由 |
| memo | string | メモ |
| changedAt | string(datetime) | 変更日時 |
| createdAt | string(datetime) | 作成日時 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | `inventoryStockId` が不足または不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |

## 備考

- 現行実装では対象実在庫の存在チェックは履歴取得前に行わない。
- 履歴が存在しない場合は空配列を返却する。
