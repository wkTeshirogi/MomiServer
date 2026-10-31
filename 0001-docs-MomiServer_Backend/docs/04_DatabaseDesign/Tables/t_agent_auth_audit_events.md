# t_agent_auth_audit_events

## 概要

登録コード発行・登録・復旧・更新・無効化・再登録の監査記録。
V22マイグレーションで追加。認証契約は[専用認証設計](../../02_SystemDesign/認証/0005_agent_authentication.md)を参照する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 採番 | 主キー | 監査ID |
| 2 | agent_id | UUID | 不可 | なし | 外部キー | 対象Agent |
| 3 | generation | BIGINT | 不可 | なし | なし | 対象登録世代 |
| 4 | action | VARCHAR(40) | 不可 | なし | なし | 安定した英語操作コード |
| 5 | actor | VARCHAR(255) | 不可 | なし | なし | 管理ユーザー名またはAgent UUID |
| 6 | reason | VARCHAR(500) | 可 | なし | なし | 管理者の状態変更理由 |
| 7 | created_at | TIMESTAMPTZ | 不可 | 現在日時 | なし | 操作日時 |

## インデックス

idの主キー。

## 外部キー

agent_id → m_agents.id。削除・更新ともNO ACTION。

## 更新制御

操作と同一トランザクションで追記する。コード・トークン・秘密鍵は記録しない。自動削除は未実装。保持期限の運用設定は別途検討。
