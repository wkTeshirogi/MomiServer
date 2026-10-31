# t_agent_service_status

## 概要

サービスマスタに対応するOSサービスとプロセスの最新実測状態を管理する。
Windows Service Control ManagerまたはLinux systemdから取得した値を保存する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | service_id | BIGINT | 不可 | なし | 主キー・外部キー | サービスマスタID |
| 2 | agent_id | UUID | 不可 | なし | 外部キー | 収集Agent ID |
| 3 | generation | BIGINT | 不可 | なし | なし | 収集時の登録世代 |
| 4 | collected_at | TIMESTAMPTZ | 不可 | なし | なし | Agentでの収集日時 |
| 5 | status | VARCHAR(20) | 不可 | なし | online / offline / unknown | OSサービス状態 |
| 6 | process_id | BIGINT | 可 | なし | 正数 | 稼働プロセスID |
| 7 | uptime_seconds | BIGINT | 不可 | なし | 0以上 | プロセス稼働秒数 |
| 8 | cpu_percent | NUMERIC(5,2) | 不可 | なし | 0から100 | プロセスCPU使用率 |
| 9 | memory_percent | NUMERIC(5,2) | 不可 | なし | 0から100 | プロセスメモリ使用率 |
| 10 | updated_at | TIMESTAMPTZ | 不可 | 現在日時 | なし | 最新状態の更新日時 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | t_agent_service_status_pkey | service_id | はい | サービス単位の最新状態 |
| 2 | idx_t_agent_service_status_agent_collected | agent_id, collected_at | いいえ | Agent単位の鮮度判定 |

## 外部キー

| No | カラム | 参照先 | 削除時 | 更新時 |
| --- | --- | --- | --- | --- |
| 1 | service_id | m_services.id | CASCADE | NO ACTION |
| 2 | agent_id | m_agents.id | CASCADE | NO ACTION |

## 更新制御

認証Agentの端末に割り当てられたサービスIDだけを受理する。
同一Agentの既存行を削除し、受信した全サービスを同一トランザクションでupsertする。

## 備考

- サービス管理機構から取得できない場合は`unknown`とし、他サービスの送信を継続する。
- macOSのサービス管理機構は未対応であり、状態は`unknown`となる。
