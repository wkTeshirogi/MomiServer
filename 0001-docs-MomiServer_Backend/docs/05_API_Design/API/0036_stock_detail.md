# 実在庫詳細取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0036 |
| 分類 | 在庫管理 |
| メソッド | POST |
| パス | `/api/stock/detail` |
| 認証 | 必須 |
| 権限 | 認証済み |
| 正常ステータス | HTTP 200 |

## 概要

指定した実在庫 ID の詳細情報を取得する。
在庫管理詳細画面で既存データを編集フォームへ反映するために使用する。

## 処理概要

1. 認証済みユーザーであることを確認する。
2. クエリパラメータ `id` に一致する `t_inventory_stocks` を取得する。
3. 取得したレコードを `ItemStockDetailDto` 形式へ変換して返却する。
4. 対象レコードが存在しない場合はエラーとする。

## パスパラメータ

なし。

## クエリパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| id | number | 必須 | 1以上 | 取得対象の実在庫 ID |

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
| 400 | `INVALID_PARAMETER` | `id` が不足または不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 500 | `INTERNAL_SERVER_ERROR` | 対象実在庫が存在しない、または予期しないエラー |

## 備考

- 現行実装では対象レコードが存在しない場合、サービス層で `IllegalArgumentException` を送出する。
- 共通例外ハンドリング上の HTTP ステータス変換は別途確認が必要である。
