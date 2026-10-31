# m_devices

## 概要

IoTデバイスの名称、アドレス、設置場所、MQTTクライアントID、状態を管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | デバイスID |
| 2 | device_name | VARCHAR(255) | 不可 | なし | なし | デバイス名 |
| 3 | address | VARCHAR(15) | 不可 | なし | なし | IPアドレス |
| 4 | location | VARCHAR(50) | 可 | なし | なし | 設置場所 |
| 5 | mqtt_client_id | VARCHAR(50) | 可 | 空文字 | なし | MQTTクライアントID |
| 6 | note | VARCHAR(500) | 可 | 空文字 | なし | 備考 |
| 7 | status | VARCHAR(50) | 不可 | なし | なし | 状態 |
| 8 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 9 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## マスタ削除制御

デバイスを通常利用から除外する場合は、物理削除ではなく `status` を削除扱いの状態へ更新する。
削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。
