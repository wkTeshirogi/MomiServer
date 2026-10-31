# バッチ手動実行要求作成 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0021 |
| 分類 | バッチ実行 |
| メソッド | POST |
| パス | `/api/batch/executions/definitions/{batchDefinitionId}` |
| 認証 | 必須 |
| 権限 | ADMIN |
| 正常ステータス | HTTP 200 |

## 概要

指定したバッチ定義から手動実行要求を作成し、対象 Agent へ MQTT で実行コマンドを publish する。
publish に成功した場合は実行状態を `dispatched` に更新する。publish に失敗した場合は `queued` のまま保持し、再 dispatch 対象とする。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| batchDefinitionId | number | 必須 | 実行対象のバッチ定義 ID |

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| target_agent_id | number | 必須 | 実行対象 Agent ID |
| test_execution | boolean | 任意 | テスト実行フラグ。未指定時は false 扱い |

## レスポンス

`BatchExecutionRequestDetailDto` を返却する。

| 項目 | 型 | 説明 |
| --- | --- | --- |
| execution_id | string(UUID) | Agent 連携用の実行要求 ID |
| execution_status | string | `queued` または `dispatched` |
| execution_command | string | Agent が実行するコマンド |
| target_agent_id | number | 対象 Agent ID |
| requested_type | string | `manual` |
| requested_by | string | 認証ユーザー名 |

## 例外

| 条件 | HTTP ステータス | 説明 |
| --- | --- | --- |
| 未認証 | 401 | JWT がない、または無効 |
| 権限不足 | 403 | ADMIN 権限がない |
| バッチ定義が存在しない | 404 | 指定 ID のバッチ定義がない |
| バッチ定義が無効 | 400 | 無効なバッチ定義は実行不可 |
| 実行コマンド未設定 | 400 | `execution_command` が未設定 |

