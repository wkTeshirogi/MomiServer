# t_external_api_key_events

## 概要

APIキーの発行・無効化監査を保持する。V37で作成する。`060_table_definition.md`テンプレートを使用した。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGINT | 不可 | IDENTITY | 主キー | 監査ID |
| 2 | api_key_id | UUID | 不可 | なし | 外部キー | 対象キー |
| 3 | event_type | VARCHAR(20) | 不可 | なし | CHECK | ISSUED、REVOKED |
| 4 | actor | VARCHAR(255) | 不可 | なし | なし | 操作者 |
| 5 | occurred_at | TIMESTAMPTZ | 不可 | now() | なし | 操作日時 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_external_api_key_events_pkey | id | はい | 監査識別 |

## 外部キー

| No | カラム | 参照先 | 削除時 | 更新時 |
| --- | --- | --- | --- | --- |
| 1 | api_key_id | t_external_api_keys.id | NO ACTION | NO ACTION |

## 更新制御

キー発行・初回無効化と同一トランザクションで追記する。無効化の再要求では重複追記しない。
秘密値・ハッシュは記録しない。認証失敗や利用履歴は既存APIアクセスログで確認する。

## 備考

- [キー管理テーブル](./t_external_api_keys.md)を参照。
