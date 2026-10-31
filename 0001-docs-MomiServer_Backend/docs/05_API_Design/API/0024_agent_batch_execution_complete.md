# Agent バッチ実行完了通知 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0024 |
| 分類 | Agent バッチ実行 |
| メソッド | POST |
| パス | `/api/v1/agent/batch/executions/{executionId}/complete` |
| 認証 | Agent専用Bearer |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent がバッチ実行の完了結果をバックエンドへ通知する。
実行要求を完了状態へ更新し、終了日時を記録したうえで、バッチ実行履歴へ保存する。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| executionId | string(UUID) | 必須 | バッチ実行要求 UUID |

## リクエストボディ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| execution_status | string | 必須 | `success`、`failed`、`timeout`、`cancelled` |
| exit_code | number | 任意 | 実行プロセスの終了コード |
| log_path | string | 任意 | 実行ログパス。最大 500 文字 |
| message | string | 任意 | 実行結果メッセージ |

## レスポンス

更新後の `BatchExecutionRequestDetailDto` を返却する。

## 関連処理

- 完了通知後、`t_batch_execution_requests` を更新する。
- 完了結果は `t_batch_execution_histories` に保存する。


認証主体の端末IDと実行要求の登録世代を照合する。別端末・旧世代は404。詳細は[専用認証API](0041_agent_registration_authentication.md)を参照する。
