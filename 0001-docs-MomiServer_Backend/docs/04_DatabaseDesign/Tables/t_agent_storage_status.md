# t_agent_storage_status

## 概要

Agentが収集したマウントポイントまたはドライブ単位の最新ストレージ状態を管理する。
端末状態スナップショットに含まれる全ストレージを毎回置き換える。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | agent_id | UUID | 不可 | なし | 複合主キー・外部キー | Agent ID |
| 2 | storage_key | VARCHAR(500) | 不可 | なし | 複合主キー | マウントポイントまたはドライブ識別子 |
| 3 | display_name | VARCHAR(500) | 不可 | なし | なし | 画面表示名 |
| 4 | total_bytes | BIGINT | 不可 | なし | 0以上 | 総容量 |
| 5 | used_bytes | BIGINT | 不可 | なし | 0以上・総容量以下 | 使用容量 |
| 6 | collected_at | TIMESTAMPTZ | 不可 | なし | なし | Agentでの収集日時 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_agent_storage_status_pkey | agent_id, storage_key | はい | Agent内のストレージ識別 |
| 2 | idx_t_agent_storage_status_collected | collected_at | いいえ | 鮮度判定・運用調査 |

## 外部キー

| No | カラム | 参照先 | 削除時 | 更新時 |
| --- | --- | --- | --- | --- |
| 1 | agent_id | m_agents.id | CASCADE | NO ACTION |

## 更新制御

同一Agentの既存行を削除して、1回の端末状態スナップショットに含まれる行を同一トランザクションで登録する。

## 備考

- API応答ではbyteをMiBへ変換する。
- 使用容量が総容量を超えるリクエストは登録しない。
