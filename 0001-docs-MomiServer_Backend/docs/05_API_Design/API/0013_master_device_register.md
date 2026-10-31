# デバイス登録・更新 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0013 |
| 分類 | デバイスマスタ |
| メソッド | POST |
| パス | `/api/master/device/register` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

デバイスを登録または更新し、登録後の詳細情報を返す。

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| id | number | 任意 | 更新対象ID |
| deviceName | string | 必須 | デバイス名 |
| address | string | 必須 | IPアドレス |
| status | string | 必須 | 状態 |
| location | string | 任意 | 設置場所 |
| mqtt_client_id | string | 任意 | MQTTクライアントID |
| note | string | 任意 | 備考 |

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

