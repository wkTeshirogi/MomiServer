# m_agents

## 概要

端末マスタに対するAgent登録状態と復旧情報。
V22マイグレーションで追加。認証契約は[専用認証設計](../../02_SystemDesign/認証/0005_agent_authentication.md)を参照する。

## カラム定義

V23追加項目:

| カラム | 型 | NULL | デフォルト | 説明 |
| --- | --- | --- | --- | --- |
| heartbeat_at | TIMESTAMPTZ | 可 | なし | 軽量ハートビートのサーバー受信日時 |
| mqtt_connected | BOOLEAN | 不可 | false | 最後に受信したAgentのMQTT接続報告 |

HTTPのlast_seen_atとheartbeat_atを区別する。接続状態は未受信unknown、3分以内online、期限超過offlineとしてAPIで導出する。

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | UUID | 不可 | なし | 主キー | バックエンド採番のAgent ID |
| 2 | device_id | BIGINT | 不可 | なし | 一意・外部キー | 端末マスタID |
| 3 | generation | BIGINT | 不可 | 1 | 正数 | 登録世代 |
| 4 | status | VARCHAR(20) | 不可 | なし | pending / registered / disabled | 登録状態 |
| 5 | mqtt_client_id | VARCHAR(100) | 不可 | なし | 一意 | Agent UUIDと世代から生成 |
| 6 | code_hash | VARCHAR(64) | 可 | なし | 一意 | 登録コードのSHA-256 |
| 7 | code_expires_at | TIMESTAMPTZ | 可 | なし | なし | コード期限 |
| 8 | registration_request_id | UUID | 可 | なし | 一意 | 確定した初回登録要求 |
| 9 | public_key | TEXT | 可 | なし | なし | Ed25519公開鍵SPKI Base64 |
| 10 | registration_response | TEXT | 可 | なし | なし | AES-GCM暗号化済み復旧応答 |
| 11 | recovery_expires_at | TIMESTAMPTZ | 可 | なし | なし | 登録復旧期限 |
| 12 | hostname | VARCHAR(255) | 可 | なし | なし | 申告ホスト名 |
| 13 | os | VARCHAR(40) | 可 | なし | なし | 申告OS |
| 14 | agent_version | VARCHAR(40) | 可 | なし | なし | 申告バージョン |
| 15 | registered_at | TIMESTAMPTZ | 可 | なし | なし | 登録受理日時 |
| 16 | last_seen_at | TIMESTAMPTZ | 可 | なし | なし | me APIの最終認証確認日時 |
| 17 | created_at | TIMESTAMPTZ | 不可 | 現在日時 | なし | 作成日時 |
| 18 | updated_at | TIMESTAMPTZ | 不可 | 現在日時 | なし | 登録管理更新日時 |

## インデックス

id、device_id、mqtt_client_id、code_hash、registration_request_idに主キーまたは一意インデックス。

## 外部キー

device_id → m_devices.id。削除・更新ともNO ACTION。

## 更新制御

無効化・再登録は状態更新。資格情報喪失時にも端末マスタを削除しない。復旧応答の期限切れ清掃は秘匿データのみを消去する。
