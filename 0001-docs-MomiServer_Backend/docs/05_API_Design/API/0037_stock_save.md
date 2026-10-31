# 実在庫登録・更新 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0037 |
| 分類 | 在庫管理 |
| メソッド | POST |
| パス | `/api/stock/save` |
| 認証 | 必須 |
| 権限 | 認証済み |
| 正常ステータス | HTTP 200 |

## 概要

実在庫情報を登録または更新する。
リクエストボディの `id` が未指定の場合は新規登録し、`id` が指定されている場合は既存実在庫を更新する。

## 処理概要

1. 認証済みユーザーであることを確認する。
2. リクエストボディの入力値を検証する。
3. 品目マスタの最低数量と現在数量から在庫ステータスを算出する。削除要求の場合のみ `deleted` を維持する。
4. `id` が未指定または 0 以下の場合、`t_inventory_stocks` に新規登録する。
5. `id` が 1 以上の場合、該当する `t_inventory_stocks` を更新する。
6. 保存後の実在庫情報を `ItemStockDetailDto` 形式で返却する。

## パスパラメータ

なし。

## クエリパラメータ

なし。

## リクエストボディ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| id | number | 任意 | 1以上 | 更新時の実在庫 ID。新規登録時は省略 |
| inventory_item_id | number | 必須 | 既存の品目 ID | 紐づける在庫品目 ID |
| storage_location | string | 必須 | 255文字以内 | 保管場所 |
| quantity | number | 必須 | 0以上 | 現在数量 |
| replenish_quantity | number | 任意 | 0以上 | 補充時の推奨数量 |
| inventory_status | string | 必須 | DB制約値 | 画面で算出した値。通常保存時はサーバーで再算出し、`deleted` は削除要求として扱う |
| expiration_date | string(date) | 任意 | 日付 | 期限日 |
| memo | string | 任意 | なし | メモ |

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
| 400 | `VALIDATION_ERROR` | 必須項目不足、文字数超過、数量不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 500 | `INTERNAL_SERVER_ERROR` | 更新対象が存在しない、外部キー制約違反、または予期しないエラー |

## 備考

- 物理削除 API は現行実装では提供しない。
- 実在庫を通常利用から除外する場合は、`inventory_status` に `deleted` を保存する。
- 通常保存時の在庫ステータスは、数量0を `out_of_stock`、数量1以上かつ最低数量以下を `low`、最低数量超過を `available` とする。
- 数量変更履歴を残す運用では、登録・更新 API ではなく実在庫数量操作 API の利用を優先する。
