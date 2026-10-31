# t_external_api_keys

## 概要

外部連携単位のAPIキーと失効状態、DB共有レート制限を保持する。V37で作成する。
`060_table_definition.md`テンプレートを使用した。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | UUID | 不可 | なし | 主キー | キー管理ID |
| 2 | name | VARCHAR(100) | 不可 | なし | なし | 連携先名 |
| 3 | key_prefix | VARCHAR(16) | 不可 | なし | なし | 原文先頭12文字 |
| 4 | key_hash | CHAR(64) | 不可 | なし | UNIQUE | SHA-256ハッシュ |
| 5 | scopes | TEXT[] | 不可 | なし | CHECK | 1件以上、定義済みスコープのみ |
| 6 | status | VARCHAR(20) | 不可 | ACTIVE | CHECK | ACTIVE、REVOKED |
| 7 | expires_at | TIMESTAMPTZ | 可 | なし | なし | 有効期限。NULLは無期限 |
| 8 | last_used_at | TIMESTAMPTZ | 可 | なし | なし | 認証・回数消費に成功した最終日時 |
| 9 | created_at | TIMESTAMPTZ | 不可 | now() | なし | 発行日時 |
| 10 | created_by | VARCHAR(255) | 不可 | なし | なし | 発行したWebユーザー名 |
| 11 | revoked_at | TIMESTAMPTZ | 可 | なし | なし | 無効化日時 |
| 12 | revoked_by | VARCHAR(255) | 可 | なし | なし | 無効化したWebユーザー名 |
| 13 | request_window | TIMESTAMPTZ | 可 | なし | なし | 正時単位の利用枠 |
| 14 | request_count | INTEGER | 不可 | 0 | 0以上 | 枠内利用回数 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_external_api_keys_pkey | id | はい | 管理操作 |
| 2 | t_external_api_keys_key_hash_key | key_hash | はい | 認証時のキー照合 |

## 外部キー

なし。操作者は監査時点の文字列として保持し、Webユーザーの更新に追随させない。

## 更新制御

無効化はステータスをREVOKEDへ変更する。再有効化・物理削除は提供しない。
期限切れはexpires_atから判定する。利用回数と最終利用日時は有効性確認を含む単一UPDATEで更新する。

## 備考

- [外部APIキー・在庫連携設計](../../02_SystemDesign/API%20Gateway/0002_external_api_keys.md)を参照。
