# バッチ実行要求詳細取得 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0022 |
| 分類 | バッチ実行 |
| メソッド | GET |
| パス | `/api/batch/executions/{executionId}` |
| 認証 | 必須 |
| 権限 | 認証済みユーザー |
| 正常ステータス | HTTP 200 |

## 概要

Agent 連携用 UUID を指定して、バッチ実行要求の現在状態と実行情報を取得する。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| executionId | string(UUID) | 必須 | バッチ実行要求 UUID |

## レスポンス

`BatchExecutionRequestDetailDto` を返却する。

| 項目 | 型 | 説明 |
| --- | --- | --- |
| id | number | DB 内部 ID |
| execution_id | string(UUID) | Agent 連携用 UUID |
| batch_definition_id | number | バッチ定義 ID |
| schedule_id | number | スケジュール ID |
| target_agent_id | number | 対象 Agent ID |
| requested_type | string | `manual` または `schedule` |
| execution_status | string | 実行状態 |
| dispatch_attempt_count | number | MQTT publish 試行回数 |
| requested_at | string | 要求作成日時 |
| dispatched_at | string | dispatch 成功日時 |
| started_at | string | 開始日時 |
| finished_at | string | 終了日時 |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| 未認証 | 401 | JWT がない、または無効 |
| 実行要求が存在しない | 404 | 指定 UUID の実行要求がない |

