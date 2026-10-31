# t_agent_mqtt_credentials

## 概要

Agent登録世代ごとのMQTT資格情報とMosquitto同期状態。V23で作成。登録済みAgentに対する資格情報の払い出しと、旧世代失効の永続的な再試行基準。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | agent_id | UUID | 不可 | なし | 複合主キー、FK | m_agents.id |
| 2 | generation | BIGINT | 不可 | なし | 複合主キー | 登録世代 |
| 3 | client_id | VARCHAR(100) | 不可 | なし | UNIQUE | MQTTのユーザー名・Client ID。世代を含む |
| 4 | encrypted_password | TEXT | 不可 | なし | なし | AES-GCM暗号文。Agent認証専用鍵を使用し、AADはmqtt:Client ID |
| 5 | sync_status | VARCHAR(20) | 不可 | pending | CHECK | pending / ready / revoked |
| 6 | updated_at | TIMESTAMPTZ | 不可 | CURRENT_TIMESTAMP | なし | 最終反映日時 |

## インデックス

主キー(agent_id,generation)とclient_idの一意索引を使用する。

## 外部キー

agent_idはm_agents.idを参照する。削除・更新のカスケードは指定しない。

## 更新制御

秘密値を暗号化してDBへ保存してからブローカーへ作成する。5秒周期で反映し、応答を確認してreadyとする。Agentが無効・世代変更・端末状態0になった場合、ブローカーで無効化を確認してrevokedにする。失敗時は状態を確定せず次回再試行する。
旧世代の未失効行が残る場合、新世代を有効化しない。資格情報行を物理削除する運用は行わない。管理画面に返すrevocation_pendingは現在のAgent状態との照合から導出し、DBのsync_statusへ格納しない。

## 備考

AGENT_RESPONSE_ENCRYPTION_KEYは短期復旧応答に加え、この長期MQTT資格情報の復号にも必要となる。
既存行がある状態で鍵だけを変更すると同じMQTTパスワードを取り出せない。
旧鍵で復号可能な間に再暗号化する運用、または旧ブローカー資格情報の失効と端末の再登録を計画する。
自動鍵ローテーションは未実装。
