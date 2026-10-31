# 実在庫一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0035 |
| 分類 | 在庫管理 |
| メソッド | POST |
| パス | `/api/stock/list` |
| 認証 | 必須 |
| 権限 | 認証済み |
| 正常ステータス | HTTP 200 |

## 概要

登録済みの実在庫情報を一覧取得する。
在庫管理一覧画面で、品目マスタ情報と組み合わせて現在数量、保管場所、在庫ステータスを表示するために使用する。

## 処理概要

1. 認証済みユーザーであることを確認する。
2. `t_inventory_stocks` を ID 昇順で取得する。
3. 取得したレコードを `ItemStockDetailDto` 形式へ変換して返却する。

## パスパラメータ

なし。

## クエリパラメータ

なし。

## リクエストボディ

なし。

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | 実在庫 ID |
| inventory_item_id | number | 品目 ID |
| storage_location | string | 保管場所 |
| quantity | number | 現在数量 |
| replenish_quantity | number | 補充時の推奨数量 |
| inventory_status | string | `available`、`low`、`out_of_stock`、`deleted` |
| expiration_date | string(date) | 期限日 |
| memo | string | メモ |
| createdAt | string(datetime) | 作成日時 |
| updatedAt | string(datetime) | 更新日時 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 未認証 |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |

## 備考

- 現行実装では検索条件、ページング、ステータス除外条件は API 側では指定しない。
- 画面側では在庫品目マスタ一覧も取得し、`inventory_item_id` で品目情報を補完する。
