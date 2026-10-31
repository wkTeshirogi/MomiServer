# 在庫品目一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0028 |
| 分類 | 在庫品目マスタ |
| メソッド | POST |
| パス | `/api/master/item/list` |
| 認証 | 必須 |
| 権限 | 認証済み（ADMIN、OPERATOR、USER） |
| 正常ステータス | HTTP 200 |

## 概要

在庫品目マスタに登録されている品目情報を一覧取得する。
備品・消耗品の品目名、カテゴリ、単位、最低数量、購入情報、品目管理ステータスを画面に表示するために使用する。

## 処理概要

1. 認証済みの通常ロールであることを確認する。
2. `m_inventory_items` を ID 昇順で取得する。
3. 取得したレコードを `ItemMasterDetailDto` 形式へ変換して返却する。

## パスパラメータ

なし。

## クエリパラメータ

なし。

## リクエストボディ

なし。

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
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 利用可能な通常ロールではない |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |

## 備考

- 現行実装では検索条件、ページング、ステータス除外条件は API 側では指定しない。
- 在庫管理画面で品目名、最低数量、単位などを表示するため、全通常ロールから参照できる。
- 画面側では取得後に品目名、カテゴリ、単位、最低数量、メーカー、型番、JANコード、ステータスを対象に絞り込み表示する。
