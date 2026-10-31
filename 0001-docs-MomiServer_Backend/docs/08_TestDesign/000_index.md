# テスト設計

## 概要

バックエンドAPI、認証、DBアクセス、例外応答のテスト観点を定義する。

## テスト設計書一覧

| No | 項目 | 設計書 |
| --- | --- | --- |
| 1 | 認証API | [認証APIテスト](./0001_auth_api_test.md) |
| 2 | マスタAPI | [マスタAPIテスト](./0002_master_api_test.md) |
| 3 | セキュリティ・エラー | [セキュリティ・エラーテスト](./0003_security_error_test.md) |
| 4 | DB | [DBテスト](./0004_database_test.md) |
| 5 | Gateway | [Gatewayテスト](./0005_gateway_test.md) |
| 6 | 通知 | [通知取得・表示・状態更新テスト](./0006_notification_test.md) |
| 7 | サービス単位アクセス制御 | [サービス単位アクセス制御テスト](./0007_service_access_control_test.md) |
| 8 | APIアクセスログ | [APIアクセスログテスト](./0008_api_access_log_test.md) |
| 9 | 外部APIキー・在庫連携 | [外部在庫APIテスト](./0009_external_inventory_test.md) |
| 10 | バックエンドCI | [バックエンドCI検証設計](./0010_backend_ci_test.md) |
| 11 | 一覧ページング | [総件数・取得上限・更新中動作の横断照合](./0011_list_pagination_crosscheck.md) |
| 12 | 車検更新 | [車検更新テスト](./0012_vehicle_inspection_renewal_test.md) |
| 13 | 個人設定 | [個人設定テスト](./0013_personal_settings_test.md) |
| 14 | 共通Clock | [共通Clockテスト](./0014_application_clock_test.md) |
| 15 | FE日時表示・APIエラー | [共通日時表示・APIエラーテスト](./0015_frontend_display_error_test.md) |
| 16 | 共通化 | [ページング・非同期取得・トークンハッシュ共通化テスト](./0016_shared_foundation_test.md) |
| 17 | Git基盤Phase 0 | [JGit Git Smart HTTP技術検証](./0017_jgit_smart_http_spike.md) |
