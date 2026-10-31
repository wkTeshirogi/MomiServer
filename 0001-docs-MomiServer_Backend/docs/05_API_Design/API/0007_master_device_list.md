# デバイス一覧取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0007 |
| 分類 | デバイスマスタ |
| メソッド | POST |
| パス | `/api/master/device/list` |
| 認証 | 必要 |
| 権限 | 管理者 |
| 正常ステータス | HTTP 200 |

## 概要

登録済みデバイスの一覧を取得する。

## 処理概要

デバイスを全件取得し、レスポンス配列へ変換する。対象データが0件の場合は空配列を返却する。並び順、ページング、絞り込み、最大取得件数はAPI仕様として定義しない。

## リクエスト

リクエストパラメータなし。

## レスポンス

JSON配列で返却する。

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
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | 管理者権限なし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |
