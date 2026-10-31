# t_agent_machine_status

## 概要

認証済みAgentが収集した端末全体の最新実測状態を管理する。
履歴ではなくAgent単位の最新スナップショットであり、登録世代と収集日時により表示への採用可否を判定する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | agent_id | UUID | 不可 | なし | 主キー・外部キー | Agent ID |
| 2 | generation | BIGINT | 不可 | なし | なし | 収集時の登録世代 |
| 3 | collected_at | TIMESTAMPTZ | 不可 | なし | なし | Agentでの収集日時 |
| 4 | cpu_percent | NUMERIC(5,2) | 不可 | なし | 0から100 | 端末CPU使用率 |
| 5 | memory_percent | NUMERIC(5,2) | 不可 | なし | 0から100 | 端末メモリ使用率 |
| 6 | cpu_temperature_c | NUMERIC(5,2) | 可 | なし | なし | CPU温度。取得不可時はNULL |
| 7 | gpu_temperature_c | NUMERIC(5,2) | 可 | なし | なし | GPU温度。取得不可時はNULL |
| 8 | uptime_seconds | BIGINT | 不可 | なし | 0以上 | OS稼働秒数 |
| 9 | updated_at | TIMESTAMPTZ | 不可 | 現在日時 | なし | 最新状態の更新日時 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_agent_machine_status_pkey | agent_id | はい | Agent単位の最新状態 |
| 2 | idx_t_agent_machine_status_collected | collected_at | いいえ | 鮮度判定・運用調査 |

## 外部キー

| No | カラム | 参照先 | 削除時 | 更新時 |
| --- | --- | --- | --- | --- |
| 1 | agent_id | m_agents.id | CASCADE | NO ACTION |

## 更新制御

Agent専用Bearer認証からAgent IDと登録世代を確定し、Agent単位でupsertする。
保存済みの収集日時より古いスナップショットは、関連ストレージ・サービスを含めて更新しない。
再登録後は世代不一致の行を画面表示へ採用しない。

## 備考

- CPU・GPU温度はセンサー非対応を0と区別するためNULLを許可する。
- 詳細は[Agent常駐・実行照合API](../../05_API_Design/API/0042_agent_runtime.md)を参照する。
