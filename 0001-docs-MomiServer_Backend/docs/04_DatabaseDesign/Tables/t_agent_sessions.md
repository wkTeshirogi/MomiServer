# t_agent_sessions

## 概要

Agent専用Bearer・更新トークンの照合値と短期再送情報。
V22マイグレーションで追加。認証契約は[専用認証設計](../../02_SystemDesign/認証/0005_agent_authentication.md)を参照する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | UUID | 不可 | なし | 主キー | 認証セッションID |
| 2 | agent_id | UUID | 不可 | なし | 外部キー | Agent ID |
| 3 | generation | BIGINT | 不可 | なし | なし | 発行時の登録世代 |
| 4 | access_hash | VARCHAR(64) | 不可 | なし | 一意 | アクセス照合値 |
| 5 | refresh_hash | VARCHAR(64) | 不可 | なし | 一意 | 現在の更新照合値 |
| 6 | access_expires_at | TIMESTAMPTZ | 不可 | なし | なし | アクセス期限 |
| 7 | refresh_expires_at | TIMESTAMPTZ | 不可 | なし | なし | 更新期限 |
| 8 | previous_refresh_hash | VARCHAR(64) | 可 | なし | なし | 直前の更新トークン照合値 |
| 9 | refresh_request_id | UUID | 可 | なし | なし | 最後に成功した更新要求 |
| 10 | refresh_response | TEXT | 可 | なし | なし | 暗号化した更新応答 |
| 11 | recovery_expires_at | TIMESTAMPTZ | 可 | なし | なし | 更新復旧期限 |
| 12 | revoked_at | TIMESTAMPTZ | 可 | なし | なし | 資格情報失効日時 |
| 13 | created_at | TIMESTAMPTZ | 不可 | 現在日時 | なし | 作成日時 |

## インデックス

idの主キー、access_hash / refresh_hashの一意インデックス。idx_agent_sessions_agent(agent_id)、idx_agent_sessions_previous_refresh(previous_refresh_hash)。

## 外部キー

agent_id → m_agents.id。削除・更新ともNO ACTION。

## 更新制御

更新時は同一行の両トークンを交換。Agent行・セッション行の順にロックする。無効化はrevoked_atを設定し、復旧応答を消去する。マスタの削除フラグではない。
