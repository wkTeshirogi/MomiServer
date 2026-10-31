# API-0048 外部APIキー管理・在庫連携

## 基本情報

| メソッド | パス | 認証・権限 | 正常応答 |
| --- | --- | --- | --- |
| GET | `/api/external-api-keys` | Web JWT・ADMIN | 200、キー情報配列 |
| POST | `/api/external-api-keys` | Web JWT・ADMIN | 200、`key`と初回限定の`secret` |
| POST | `/api/external-api-keys/{id}/revoke` | Web JWT・ADMIN | 200、無効化済みキー情報 |
| GET | `/api/v1/external/inventory/stocks` | X-API-Key・`inventory:read` | 200、在庫配列 |
| GET | `/api/v1/external/inventory/stocks/{id}` | X-API-Key・`inventory:read` | 200、在庫詳細 |
| POST | `/api/v1/external/inventory/stocks/{id}/operations` | X-API-Key・`inventory:write` | 200、操作後在庫詳細 |

## 概要

`070_api_design.md`を基に、同じ認証境界を持つ操作群の契約を整理する。
処理・状態・エラー方針は[外部APIキー・在庫連携設計](../../02_SystemDesign/API%20Gateway/0002_external_api_keys.md)を参照する。

## パスパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| 管理APIのid | UUID | はい | 発行済みキー | キー管理ID |
| 在庫APIのid | long | はい | 公開対象の実在庫 | 品目IDではなく実在庫ID |

## クエリパラメータ

在庫一覧にのみ適用する。

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| limit | integer | いいえ | 1～100、既定20 | 取得件数 |
| offset | integer | いいえ | 0以上、既定0 | ID昇順の開始位置 |

## リクエストボディ

### キー発行

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| name | string | はい | 空白のみ不可、100文字以下 | 連携先名 |
| scopes | string[] | はい | 1件以上、定義済みスコープのみ | 許可操作 |
| expiresAt | ISO 8601日時 | いいえ | 未来日時、タイムゾーン付き | 未指定・NULLは無期限 |

### 数量操作

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| operationId | UUID | はい | 同一業務操作の再送で変更しない | 重複防止用ID |
| changeType | string | はい | `inbound`、`outbound`、`stocktake` | 入庫・出庫・棚卸し |
| quantity | integer | はい | 0～2147483647 | 入出庫は増減量、棚卸しは実測数量 |
| reason | string | いいえ | 255文字以下 | 変更理由 |

## レスポンス

キー管理情報は`id`、`name`、`keyPrefix`、`scopes`、`status`、`expiresAt`、`lastUsedAt`、
`createdAt`、`createdBy`、`revokedAt`、`revokedBy`を返す。秘密値・ハッシュは一覧・無効化応答へ含めない。

在庫情報は`id`、`inventoryItemId`、`itemName`、`manufacturer`、`modelNumber`、`category`、
`storageLocation`、`quantity`、`minimumQuantity`、`unit`、`inventoryStatus`を返す。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` / `INVALID_REQUEST` | 入力・数量不正 |
| 401 | `INVALID_API_KEY` | 外部キー不正・失効 |
| 403 | `ACCESS_DENIED` | スコープ・権限不足 |
| 404 | `RESOURCE_NOT_FOUND` | 公開対象在庫なし |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | 同じ操作IDで異なる内容 |
| 429 | `RATE_LIMIT_EXCEEDED` | 時間あたり上限超過 |
| 503 | `API_KEY_UNAVAILABLE` | キー検証不可 |

## 備考

- バージョン公開状態の404・410・503は既存Gateway方針に従う。
- 管理APIへAPIキーを渡しても認証できない。管理APIはWebユーザーJWT専用。
- 同一操作の再送でも、有効なキーと更新スコープは毎回必要。
