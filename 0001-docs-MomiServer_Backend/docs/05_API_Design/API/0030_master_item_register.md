# 在庫品目登録・更新 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0030 |
| 分類 | 在庫品目マスタ |
| メソッド | POST |
| パス | `/api/master/item/register` |
| 認証 | 必須 |
| 権限 | ADMIN |
| 正常ステータス | HTTP 200 |

## 概要

在庫品目マスタを登録または更新する。
リクエストボディの `id` が未指定の場合は新規登録し、`id` が指定されている場合は既存品目を更新する。

## 処理概要

1. 認証済みユーザーが ADMIN 権限を持つことを確認する。
2. リクエストボディの入力値を検証する。
3. `id` が未指定または 0 以下の場合、`m_inventory_items` に新規登録する。
4. `id` が 1 以上の場合、該当する `m_inventory_items` を更新する。
5. 保存後の品目情報を `ItemMasterDetailDto` 形式で返却する。

## パスパラメータ

なし。

## クエリパラメータ

なし。

## リクエストボディ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| id | number | 任意 | 1以上 | 更新時の品目 ID。新規登録時は省略 |
| item_name | string | 必須 | 255文字以内 | 品目名 |
| category | string | 必須 | 100文字以内 | カテゴリ |
| unit | string | 必須 | 50文字以内 | 単位 |
| min_quantity | number | 必須 | 0以上 | 品目単位の標準最低数量 |
| manufacturer | string | 任意 | 255文字以内 | メーカー |
| model_number | string | 任意 | 255文字以内 | 型番 |
| jan_code | string | 任意 | 20文字以内 | JANコード |
| purchase_url | string | 任意 | なし | 購入URL |
| status | string | 必須 | 50文字以内、DB制約値 | `active`、`disabled`、`deleted` |
| memo | string | 任意 | なし | メモ |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | 品目 ID |
| item_name | string | 品目名 |
| category | string | カテゴリ |
| unit | string | 単位 |
| min_quantity | number | 品目単位の標準最低数量 |
| manufacturer | string | メーカー |
| model_number | string | 型番 |
| jan_code | string | JANコード |
| purchase_url | string | 購入URL |
| status | string | `active`、`disabled`、`deleted` |
| memo | string | メモ |
| createdAt | string(datetime) | 作成日時 |
| updatedAt | string(datetime) | 更新日時 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 必須項目不足、文字数超過、最低数量の不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | ADMIN 権限がない |
| 500 | `INTERNAL_SERVER_ERROR` | 更新対象が存在しない、または予期しないエラー |

## 備考

- 物理削除 API は現行実装では提供しない。
- 品目を通常利用から除外する場合は、`status` に `deleted` を保存する。
- フロントエンドは JANコードを数字のみ 13 桁以内に整形するが、バックエンドの入力制約は 20 文字以内である。
