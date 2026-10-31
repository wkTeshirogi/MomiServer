# API設計

- [API-0048 外部APIキー管理・在庫連携](API/0048_external_inventory.md)

- [Agent常駐・実行照合API](API/0042_agent_runtime.md): MQTT接続設定、環境、状態照合、ハートビート、結果出力。


## 基本方針

バックエンドはREST APIとして提供する。

| 項目 | 内容 |
| --- | --- |
| 通信方式 | HTTPS |
| データ形式 | JSON |
| 文字コード | UTF-8 |
| APIプレフィックス | `/api` |
| OpenAPI定義 | `/v3/api-docs` |
| Swagger UI | `/swagger-ui.html` |
| 認証方式 | WebはJWT、Agentは専用Bearer、IoTはGateway設定に応じたAPIキー、専用外部APIはDB管理キーとスコープ |

## 認証方式

| 項目 | 内容 |
| --- | --- |
| 認証ヘッダー | `Authorization: Bearer {accessToken}` |
| 公開API | `/api/auth/login`、`/api/auth/refresh`、`/api/auth/logout`、Agentの登録・challenge・更新用POST、設定に応じたOpenAPI経路 |
| 認証必須API | Agent通常APIは専用Bearer、Web APIはユーザーJWT。IoTはGateway設定に従い、専用外部APIは常にキー必須 |
| 管理者権限必須API | `/api/auth/settings`、原則として `/api/master/**`。ただし在庫画面で使用する品目一覧・詳細取得は全通常ロールに許可する |

OpenAPIは各Controllerから生成する。
`GATEWAY_OPENAPI_PUBLIC_ENABLED=false` の場合、`/v3/api-docs` とSwagger UIの閲覧にはADMIN権限が必要となる。
Agent・IoTのAPIバージョン公開状態は `m_api_versions` で判定する。
詳細は[Gateway基本設計](../02_SystemDesign/API%20Gateway/0001_gateway_basic_design.md)を参照する。

## 外部連携API認証

Agent連携APIは `/api/v1/agent` 配下で提供し、通常APIにはAgent専用Bearerを使用する。
登録・challenge・更新用POSTは専用の認証手順を使用する。IoT APIにはGateway設定に応じたAPIキーを使用する。
Gatewayに関する現行・旧仕様の分類は[基本設計](../02_SystemDesign/API%20Gateway/0001_gateway_basic_design.md)を参照する。

## マスタ削除方針

マスタデータの削除操作は、全て物理削除ではなくステータスを削除扱いの状態へ更新する。
削除状態を `delete_flag`、`deleted`、`is_deleted` などの削除専用フラグでは管理しない。
`enabled` などの有効状態項目は有効 / 無効の切り替えに限定し、削除状態の管理には使用しない。

## 共通エラー

| HTTPステータス | code | 発生条件 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 入力値の検証エラー |
| 400 | `INVALID_PARAMETER` | 必須パラメータ不足、不正値 |
| 400 | `MALFORMED_REQUEST` | JSON形式不正、リクエスト読み取り不可 |
| 401 | `AUTHENTICATION_FAILED` | ログイン認証失敗 |
| 401 | `INVALID_TOKEN` | トークン不正、期限切れ、失効済み |
| 401 | `UNAUTHORIZED` | 未認証アクセス |
| 403 | `ACCESS_DENIED` | 権限不足 |
| 404 | `RESOURCE_NOT_FOUND` | 指定リソースなし |
| 500 | `DATABASE_ERROR` | DB処理エラー |
| 500 | `INTERNAL_SERVER_ERROR` | 予期しないエラー |

## API一覧

登録・監視・復旧の追加候補は[エージェント連携API追加案](./010_agent_api_proposal.md)を参照してください。
管理画面からのファイル本体登録は[バッチ定義配布ファイル アップロード登録API案](./API/0040_batch_file_upload_register.md)を参照してください。
追加案のパス・契約は未確定です。下のAPI-0031～0034は旧設計案であり、現行の提供APIには含みません。

| No | 分類 | メソッド | パス | 認証 | 権限 | 設計書 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 認証 | POST | `/api/auth/login` | 不要 | なし | [ログイン](./API/0002_auth_login.md) |
| 2 | 認証 | POST | `/api/auth/refresh` | 不要 | なし | [トークン更新](./API/0003_auth_refresh.md) |
| 3 | 認証 | POST | `/api/auth/logout` | 任意 | なし | [ログアウト](./API/0011_auth_logout.md) |
| 4 | 認証 | POST | `/api/auth/settings` | 必要 | 管理者 | [ユーザー設定変更](./API/0004_auth_settings.md) |
| 5 | ユーザーマスタ | POST | `/api/master/user/register` | 必要 | 管理者 | [ユーザー登録・更新](./API/0012_master_user_register.md) |
| 6 | ユーザーマスタ | POST | `/api/master/user/list` | 必要 | 管理者 | [ユーザー一覧取得](./API/0005_master_user_list.md) |
| 7 | ユーザーマスタ | POST | `/api/master/user/detail` | 必要 | 管理者 | [ユーザー詳細取得](./API/0006_master_user_detail.md) |
| 8 | デバイスマスタ | POST | `/api/master/device/register` | 必要 | 管理者 | [デバイス登録・更新](./API/0013_master_device_register.md) |
| 9 | デバイスマスタ | POST | `/api/master/device/list` | 必要 | 管理者 | [デバイス一覧取得](./API/0007_master_device_list.md) |
| 10 | デバイスマスタ | POST | `/api/master/device/detail` | 必要 | 管理者 | [デバイス詳細取得](./API/0008_master_device_detail.md) |
| 11 | サービスマスタ | POST | `/api/master/service/register` | 必要 | 管理者 | [サービス登録・更新](./API/0014_master_service_register.md) |
| 12 | サービスマスタ | POST | `/api/master/service/list` | 必要 | 管理者 | [サービス一覧取得](./API/0009_master_service_list.md) |
| 13 | サービスマスタ | POST | `/api/master/service/detail` | 必要 | 管理者 | [サービス詳細取得](./API/0010_master_service_detail.md) |
| 14 | バッチ定義マスタ | POST | `/api/master/batch/list` | 必要 | 管理者 | [バッチ定義一覧取得](./API/0015_batch_definition_list.md) |
| 15 | バッチ定義マスタ | POST | `/api/master/batch/detail` | 必要 | 管理者 | [バッチ定義詳細取得](./API/0016_batch_definition_detail.md) |
| 16 | バッチ定義マスタ | POST | `/api/master/batch/register` | 必要 | 管理者 | [バッチ定義登録・更新](./API/0017_batch_definition_register.md) |
| 17 | バッチスケジュールマスタ | POST | `/api/master/batch/schedule/list` | 必要 | 管理者 | [バッチスケジュール一覧取得](./API/0018_batch_schedule_list.md) |
| 18 | バッチスケジュールマスタ | POST | `/api/master/batch/schedule/detail` | 必要 | 管理者 | [バッチスケジュール詳細取得](./API/0019_batch_schedule_detail.md) |
| 19 | バッチスケジュールマスタ | POST | `/api/master/batch/schedule/register` | 必要 | 管理者 | [バッチスケジュール登録・更新](./API/0020_batch_schedule_register.md) |
| 20 | 在庫品目マスタ | POST | `/api/master/item/list` | 必要 | 認証済み | [在庫品目一覧取得](./API/0028_master_item_list.md) |
| 21 | 在庫品目マスタ | POST | `/api/master/item/detail` | 必要 | 認証済み | [在庫品目詳細取得](./API/0029_master_item_detail.md) |
| 22 | 在庫品目マスタ | POST | `/api/master/item/register` | 必要 | 管理者 | [在庫品目登録・更新](./API/0030_master_item_register.md) |

## 今回追加分

| No | 分類 | メソッド | パス | 認証 | 権限 | 設計書 |
| --- | --- | --- | --- | --- | --- | --- |
| 23 | バッチ実行 | POST | `/api/batch/executions/definitions/{batchDefinitionId}` | 必須 | ADMIN | [バッチ手動実行要求作成](./API/0021_batch_execution_create_manual.md) |
| 24 | バッチ実行 | GET | `/api/batch/executions/{executionId}` | 必須 | 認証済み | [バッチ実行要求詳細取得](./API/0022_batch_execution_detail.md) |
| 25 | Agent バッチ実行 | POST | `/api/v1/agent/batch/executions/{executionId}/start` | Agent専用Bearer | Agent連携 | [Agent バッチ実行開始通知](./API/0023_agent_batch_execution_start.md) |
| 26 | Agent バッチ実行 | POST | `/api/v1/agent/batch/executions/{executionId}/complete` | Agent専用Bearer | Agent連携 | [Agent バッチ実行完了通知](./API/0024_agent_batch_execution_complete.md) |
| 27 | バッチ実行ファイル | POST | `/api/batch/executions/{executionId}/files` | 必須 | ADMIN | [バッチ実行ファイル登録](./API/0025_batch_execution_file_register.md) |
| 28 | Agent バッチ実行ファイル | GET | `/api/v1/agent/batch/executions/{executionId}/files` | Agent専用Bearer | Agent連携 | [Agent バッチ実行ファイル一覧取得](./API/0026_agent_batch_execution_file_list.md) |
| 29 | Agent バッチ実行ファイル | GET | `/api/v1/agent/batch/executions/{executionId}/files/{fileId}/download` | Agent専用Bearer | Agent連携 | [Agent バッチ実行ファイルダウンロード](./API/0027_agent_batch_execution_file_download.md) |
| 34 | 在庫管理 | POST | `/api/stock/list` | 必須 | 認証済み | [実在庫一覧取得](./API/0035_stock_list.md) |
| 35 | 在庫管理 | POST | `/api/stock/detail` | 必須 | 認証済み | [実在庫詳細取得](./API/0036_stock_detail.md) |
| 36 | 在庫管理 | POST | `/api/stock/save` | 必須 | 認証済み | [実在庫登録・更新](./API/0037_stock_save.md) |
| 37 | 在庫管理 | POST | `/api/stock/operation` | 必須 | 認証済み | [実在庫数量操作](./API/0038_stock_operation.md) |
| 38 | 在庫管理 | POST | `/api/stock/history` | 必須 | 認証済み | [実在庫履歴取得](./API/0039_stock_history.md) |

## 旧ファイル転送API案（未実装）

以下のパスはControllerに実装されていない。旧Gateway APIキー認証を前提とした契約は現行仕様ではない。実装を検討する場合は、Agent専用Bearerと対象端末・登録世代の認可を含めて再設計する。

| No | 旧パス | 設計案 |
| --- | --- | --- |
| 30 | `/api/v1/agent/upload/{category}` | [API-0031 ファイルアップロード](./API/0031_agent_file_upload.md) |
| 31 | `/api/v1/agent/download/{downloadUuid}` | [API-0032 ファイルダウンロード](./API/0032_agent_file_download.md) |
| 32 | `/api/v1/agent/download/{downloadUuid}/complete` | [API-0033 完了通知](./API/0033_agent_file_download_complete.md) |
| 33 | `/api/v1/agent/download/{downloadUuid}/failed` | [API-0034 失敗通知](./API/0034_agent_file_download_failed.md) |


## スケジュール実行状況・ログ

- [API-0043 スケジュール実行状況取得](./API/0043_batch_schedule_executions.md)
- [API-0044 スケジュール完了ログ取得](./API/0044_batch_schedule_execution_logs.md)

## 状態監視

- [API-0045 マシン・サービス状態取得／リアルタイム配信](./API/0045_status_streaming.md)

## ログ管理

- [API-0046 APIアクセスログ取得](./API/0046_api_access_logs.md)

## 個人設定

- [API-0049 本人のユーザー名・パスワード・テーマ](./API/0049_personal_settings.md)

## 車両管理

- [API-0047 車両管理](./API/0047_vehicle_management.md)
- `POST`・`GET` `/api/vehicle-imports/**`: ADMIN・OPERATOR向けPDF・画像取込、確認、原本確認、登録、取消。

## バッチ保守

- [API-0050 バッチ保守状態取得・変更](./API/0050_batch_maintenance.md)

## Agent登録・専用認証

- [Agent登録・専用認証API](./API/0041_agent_registration_authentication.md)

2026年9月23日、既存の開始・完了・設定・状態・実行ファイル取得はAgent専用Bearerと端末・登録世代で保護した。下記の追加案は実装済みと混同しない。
