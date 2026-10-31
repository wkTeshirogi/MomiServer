# デバイス詳細取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0008 |
| 分類 | デバイスマスタ |
| メソッド | POST |
| パス | `/api/master/device/detail` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

指定デバイスの詳細情報を取得する。

## 処理概要

指定されたデバイスIDに一致するデバイスを1件取得し、レスポンスへ変換する。対象データが存在しない場合は404を返却する。

## リクエストパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| id | number | 必須 | デバイスID。必須・範囲制約はAPIレベルでは明示しない |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | デバイスID |
| devicename | string | デバイス名 |
| address | string | IPアドレス |
| location | string | 設置場所 |
| mqtt_client_id | string | MQTTクライアントID |
| note | string | 備考 |
| status | string | 状態 |
| createdAt | string | 作成日時 |
| updatedAt | string | 更新日時 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | デバイスID不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 管理者権限なし |
| 404 | `RESOURCE_NOT_FOUND` | 対象デバイスなし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |
