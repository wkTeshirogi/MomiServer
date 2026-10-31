# 外部接続設計

## PostgreSQL

| 項目 | 内容 |
| --- | --- |
| 用途 | 業務データ、認証情報、マスタ情報の保存 |
| 接続方式 | JDBC |
| スキーマ管理 | Flyway |
| 型安全SQL | jOOQ |

## MQTT

| 項目 | 内容 |
| --- | --- |
| 有効化 | 有効 |
| Broker URL | 環境変数で変更可能 |
| Client ID | 環境変数で変更可能 |
| QoS | 1 |
| 接続タイムアウト | 30秒 |
| Keep Alive | 60秒 |
| 自動再接続 | 有効 |

## 通知

| 項目 | 内容 |
| --- | --- |
| 通知方式 | Discord Webhook |
| 既定状態 | 無効 |
| Webhook URL | 環境変数で設定 |

## フロントエンド接続

| 項目 | 内容 |
| --- | --- |
| CORS許可オリジン | `http://localhost:5173` |
| 許可メソッド | GET、POST、PUT、DELETE、OPTIONS |
| 認証情報 | 許可 |
