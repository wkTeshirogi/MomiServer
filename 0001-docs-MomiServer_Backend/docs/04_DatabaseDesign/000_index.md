# DB設計

- [t_agent_mqtt_credentials](Tables/t_agent_mqtt_credentials.md): Agent世代別MQTT資格情報と同期状態（V23）。


## 概要

バックエンドで利用するPostgreSQLのテーブル、初期データ、更新日時制御を定義する。

## マスタ削除方針

マスタデータを通常利用から除外する場合は、物理削除ではなくステータスを削除扱いの状態へ更新する。
全てのマスタで削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。
`enabled` などの有効状態項目は有効 / 無効の切り替えに限定し、削除状態の管理には使用しない。

## APIバージョン管理方針

Agentや自作IoT機器など外部連携APIのバージョンはDBで管理する。
DBではAPI実装そのものではなく、有効状態、非推奨状態、廃止予定、外部クライアントごとの利用可能バージョンを管理する。
APIの実装ルートはソースコードとOpenAPIで管理する。

| 管理対象 | 管理方針 |
| --- | --- |
| APIバージョン | `v1`、`v2` などのバージョンと状態を管理する |
| バージョン状態 | `active`、`deprecated`、`retired` などで管理する |
| 外部クライアント | Agent、自作IoT機器ごとの利用可能バージョンを管理する |
| 廃止予定 | 非推奨化日、廃止予定日を管理する |

## テーブル一覧

| No | テーブル | 用途 | 設計書 |
| --- | --- | --- | --- |
| 1 | m_users | ユーザー認証・権限管理 | [m_users](./Tables/m_users.md) |
| 2 | m_services | 監視対象サービス管理 | [m_services](./Tables/m_services.md) |
| 3 | m_devices | デバイス管理 | [m_devices](./Tables/m_devices.md) |
| 4 | t_auth_sessions | 認証セッション管理 | [t_auth_sessions](./Tables/t_auth_sessions.md) |
| 5 | m_batch_definitions | バッチ定義管理 | [m_batch_definitions](./Tables/m_batch_definitions.md) |
| 6 | m_batch_schedules | バッチスケジュール管理 | [m_batch_schedules](./Tables/m_batch_schedules.md) |
| 7 | m_batch_definition_parameters | バッチ定義パラメータ管理 | [m_batch_definition_parameters](./Tables/m_batch_definition_parameters.md) |
| 8 | t_batch_execution_histories | バッチ実行履歴管理 | [t_batch_execution_histories](./Tables/t_batch_execution_histories.md) |

## 関連設計

| No | 項目 | 設計書 |
| --- | --- | --- |
| 1 | 更新日時制御 | [更新日時制御](./0001_updated_at_trigger.md) |
| 2 | 初期データ | [初期データ設計](./0002_initial_data.md) |

## 今回追加分

| No | テーブル | 用途 | 設計書 |
| --- | --- | --- | --- |
| 9 | t_batch_execution_requests | バッチ実行要求管理 | [t_batch_execution_requests](./Tables/t_batch_execution_requests.md) |
| 10 | t_batch_execution_files | バッチ実行ファイル管理 | [t_batch_execution_files](./Tables/t_batch_execution_files.md) |
| 11 | m_inventory_items | 在庫品目マスタ | [m_inventory_items](./Tables/m_inventory_items.md) |
| 12 | t_inventory_stocks | 実在庫管理 | [t_inventory_stocks](./Tables/t_inventory_stocks.md) |
| 13 | t_inventory_stock_histories | 在庫変動履歴管理 | [t_inventory_stock_histories](./Tables/t_inventory_stock_histories.md) |
| 14 | m_batch_files | バッチ定義配布ファイル管理 | [m_batch_files](./Tables/m_batch_files.md) |
| 15 | t_batch_transfer_files | バッチ関連ファイル転送管理 | [t_batch_transfer_files](./Tables/t_batch_transfer_files.md) |
| 16 | t_notifications | ブラウザ通知本体管理 | [t_notifications](./Tables/t_notifications.md) |
| 17 | t_user_notifications | ユーザー別ブラウザ通知状態管理 | [t_user_notifications](./Tables/t_user_notifications.md) |
| 18 | m_notification_roles | 通知専用ロール管理 | [m_notification_roles](./Tables/m_notification_roles.md) |
| 19 | t_user_notification_roles | ユーザー別通知ロール割当管理 | [t_user_notification_roles](./Tables/t_user_notification_roles.md) |
| 20 | t_notification_route_settings | 通知ルート設定管理 | [t_notification_route_settings](./Tables/t_notification_route_settings.md) |
| 21 | t_user_notification_preferences | ユーザー別通知受信設定管理 | [t_user_notification_preferences](./Tables/t_user_notification_preferences.md) |
| 22 | t_batch_file_upload_requests（案） | バッチ定義画面のファイル登録要求・重複防止・保存結果管理 | [t_batch_file_upload_requests](./Tables/t_batch_file_upload_requests.md) |
| 23 | t_api_access_logs | APIアクセス履歴管理 | [t_api_access_logs](./Tables/t_api_access_logs.md) |
| 24 | t_vehicles | 車両・車検期限管理 | [t_vehicles](./Tables/t_vehicles.md) |
| 25 | t_vehicle_inspection_histories | 車検期限変更履歴管理 | [t_vehicle_inspection_histories](./Tables/t_vehicle_inspection_histories.md) |
| 26 | t_vehicle_imports | 車検証取込の候補値・状態・一時保存参照 | [t_vehicle_imports](./Tables/t_vehicle_imports.md) |
| 27 | t_vehicle_inspection_notifications | 車検期限通知の重複防止記録 | [t_vehicle_inspection_notifications](./Tables/t_vehicle_inspection_notifications.md) |
| 28 | t_external_api_keys | 外部APIキーとDB共有レート制限 | [t_external_api_keys](./Tables/t_external_api_keys.md) |
| 29 | t_external_api_key_events | 外部APIキー発行・無効化監査 | [t_external_api_key_events](./Tables/t_external_api_key_events.md) |
| 30 | t_external_inventory_operations | 外部数量操作の重複防止 | [t_external_inventory_operations](./Tables/t_external_inventory_operations.md) |


## Agent登録・認証（2026/09/23）

| テーブル | 用途 |
| --- | --- |
| [m_agents](./Tables/m_agents.md) | 端末マスタに対するAgent登録状態と復旧情報。 |
| [t_agent_sessions](./Tables/t_agent_sessions.md) | Agent専用Bearer・更新トークンの照合値と短期再送情報。 |
| [t_agent_auth_challenges](./Tables/t_agent_auth_challenges.md) | 短期・使い捨ての端末鍵保有確認nonce。 |
| [t_agent_auth_rate_limits](./Tables/t_agent_auth_rate_limits.md) | バックエンド複数プロセス間で共有する要求回数。 |
| [t_agent_auth_audit_events](./Tables/t_agent_auth_audit_events.md) | 登録コード発行・登録・復旧・更新・無効化・再登録の監査記録。 |
| [t_agent_machine_status](./Tables/t_agent_machine_status.md) | Agentが収集した端末全体の最新実測状態。 |
| [t_agent_storage_status](./Tables/t_agent_storage_status.md) | Agentが収集したストレージ単位の最新使用量。 |
| [t_agent_service_status](./Tables/t_agent_service_status.md) | Agentが収集したOSサービス・プロセスの最新状態。 |

- [t_batch_global_settings](Tables/t_batch_global_settings.md): バッチ共通設定と添付上限・作業領域保持。
