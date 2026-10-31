# 在庫品目詳細取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0029 |
| 分類 | 在庫品目マスタ |
| メソッド | POST |
| パス | `/api/master/item/detail` |
| 認証 | 必須 |
| 権限 | 認証済み（ADMIN、OPERATOR、USER） |
| 正常ステータス | HTTP 200 |

## 概要

指定した品目 ID の在庫品目マスタ情報を取得する。
指定IDに紐づく品目マスタ情報を参照する画面・処理で使用する。

## 処理概要

1. 認証済みの通常ロールであることを確認する。
2. クエリパラメータ `id` に一致する `m_inventory_items` を取得する。
3. 取得したレコードを `ItemMasterDetailDto` 形式へ変換して返却する。
4. 対象レコードが存在しない場合はエラーとする。

## パスパラメータ

なし。

## クエリパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| id | number | 必須 | 1以上 | 取得対象の品目 ID |

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
| 400 | `INVALID_PARAMETER` | `id` が不足または不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 利用可能な通常ロールではない |
| 500 | `INTERNAL_SERVER_ERROR` | 対象品目が存在しない、または予期しないエラー |

## 備考

- 現行実装では対象レコードが存在しない場合、サービス層で `IllegalArgumentException` を送出する。
- 参照APIであるため全通常ロールから利用できるが、品目マスタの登録・更新APIはADMINに限定する。
- 共通例外ハンドリング上の HTTP ステータス変換は実装確認事項として扱う。
