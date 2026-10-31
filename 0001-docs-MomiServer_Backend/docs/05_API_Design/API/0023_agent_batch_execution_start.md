# Agent バッチ実行開始通知 API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0023 |
| 分類 | Agent バッチ実行 |
| メソッド | POST |
| パス | `/api/v1/agent/batch/executions/{executionId}/start` |
| 認証 | Agent専用Bearer |
| 権限 | Agent連携 |
| 正常ステータス | HTTP 200 |

## 概要

Agent がバッチ実行を開始したことをバックエンドへ通知する。
対象実行要求の状態が `queued` または `dispatched` の場合、`running` へ更新し、開始日時を記録する。

## パスパラメータ

| 項目 | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| executionId | string(UUID) | 必須 | バッチ実行要求 UUID |

## リクエストボディ

なし。

## レスポンス

更新後の `BatchExecutionRequestDetailDto` を返却する。

## 注意事項

- `Authorization: Bearer` にAgent専用アクセストークンを指定する。
- 管理ユーザーJWTや `X-API-Key` では認証しない。
- Agent経路はGatewayのAPIキー検証・レート制限の対象外とする。


認証主体の端末IDと実行要求の登録世代を照合する。別端末・旧世代は404。詳細は[専用認証API](0041_agent_registration_authentication.md)を参照する。
