# t_agent_auth_challenges

## 概要

短期・使い捨ての端末鍵保有確認nonce。
V22マイグレーションで追加。認証契約は[専用認証設計](../../02_SystemDesign/認証/0005_agent_authentication.md)を参照する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | nonce_hash | VARCHAR(64) | 不可 | なし | 主キー | nonceのSHA-256 |
| 2 | expires_at | TIMESTAMPTZ | 不可 | なし | なし | 2分後の期限 |

## インデックス

nonce_hashの主キー。

## 外部キー

なし。

## 更新制御

署名確認後に期限内の行を原子的に削除する。登録・更新と同一トランザクション。期限切れも定期清掃する。業務マスタではない。
