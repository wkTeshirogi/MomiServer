# t_external_inventory_operations

## 概要

外部数量操作の二重反映を防ぐ要求識別と初回応答を保持する。V37で作成する。
`060_table_definition.md`テンプレートを使用した。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | api_key_id | UUID | 不可 | なし | 複合主キー・外部キー | 操作したキー |
| 2 | operation_id | UUID | 不可 | なし | 複合主キー | 外部連携先が生成する操作ID |
| 3 | request_hash | CHAR(64) | 不可 | なし | なし | 対象在庫IDと正規化入力のSHA-256 |
| 4 | response | JSONB | 可 | なし | なし | 操作後在庫の初回応答 |
| 5 | created_at | TIMESTAMPTZ | 不可 | now() | なし | 初回操作日時 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_external_inventory_operations_pkey | api_key_id、operation_id | はい | 同時再送も含む重複防止 |

## 外部キー

| No | カラム | 参照先 | 削除時 | 更新時 |
| --- | --- | --- | --- | --- |
| 1 | api_key_id | t_external_api_keys.id | NO ACTION | NO ACTION |

## 更新制御

在庫更新・変更履歴と同じトランザクションで要求枠を確保し、初回応答を保存する。
responseがNULLなのは処理中だけで、正常確定時には応答を保存する。失敗時は枠ごとロールバックする。
同時再送は一意制約で待機し、確定済みの入力ハッシュと初回応答を確認する。
自動削除しない。保持削除する場合は古い操作IDの再実行リスクを別途設計する。

## 備考

- [外部APIキー・在庫連携設計](../../02_SystemDesign/API%20Gateway/0002_external_api_keys.md)を参照。
