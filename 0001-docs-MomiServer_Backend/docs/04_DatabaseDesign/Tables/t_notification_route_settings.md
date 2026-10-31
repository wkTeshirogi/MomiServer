# t_notification_route_settings

## 概要

通知種別と発生元ごとに、通知先となる通知専用ロールまたは個別ユーザーを管理する。

`source_id` がNULLの場合は全体設定として扱い、値がある場合は特定の発生元データ向けの個別設定として扱う。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | DB内部ID |
| 2 | notification_type | VARCHAR(50) | 不可 | なし | CHECK | 通知種別 |
| 3 | source_type | VARCHAR(50) | 不可 | なし | CHECK | 通知発生元種別 |
| 4 | source_id | VARCHAR(100) | 可 | なし | なし | 発生元データID。NULLは全体設定 |
| 5 | target_type | VARCHAR(20) | 不可 | なし | CHECK | 通知対象種別 |
| 6 | target_role | VARCHAR(50) | 可 | なし | 外部キー | 通知ロール通知時の対象ロールコード |
| 7 | target_user_id | BIGINT | 可 | なし | 外部キー | ユーザー通知時の対象ユーザーID |
| 8 | minimum_level | VARCHAR(20) | 不可 | info | CHECK | 通知対象とする最低通知レベル |
| 9 | browser_enabled | BOOLEAN | 不可 | true | なし | ブラウザ通知有効フラグ |
| 10 | discord_enabled | BOOLEAN | 不可 | false | なし | Discord通知有効フラグ |
| 11 | enabled | BOOLEAN | 不可 | true | なし | ルート設定有効フラグ |
| 12 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 13 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 通知対象種別

| 値 | 内容 |
| --- | --- |
| notification_role | `target_role` に一致する通知専用ロールを持つユーザーを通知対象にする |
| user | `target_user_id` のユーザーを通知対象にする |

## 制約

| No | 制約 | 内容 |
| --- | --- | --- |
| 1 | chk_t_notification_route_settings_target | `notification_role` の場合は `target_role` のみ、`user` の場合は `target_user_id` のみを必須にする |
| 2 | fk_t_notification_route_settings_target_user | 個別ユーザー通知先を `m_users.id` に紐づける |
| 3 | fk_t_notification_route_settings_target_role | 通知ロール通知先を `m_notification_roles.role_code` に紐づける |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_notification_route_settings_lookup | notification_type, source_type, source_id, enabled | 通知発生時のルート検索 |
| 2 | idx_t_notification_route_settings_role | target_role | 通知ロール通知ルートの検索 |
| 3 | idx_t_notification_route_settings_user | target_user_id | ユーザー通知ルートの検索 |

## 関連実装

| 種別 | クラス | 内容 |
| --- | --- | --- |
| Repository | `common.repository.NotificationRepository` | 通知ルートからブラウザ通知対象ユーザーとDiscord通知要否を解決する |
| Enum | `common.model.NotificationTargetType` | `notification_role` / `user` の通知対象種別を表す |

## 備考

在庫管理通知では、全体設定を `source_id = NULL`、商品または在庫単位の個別設定を `source_id = 対象ID` として登録する。
同じユーザーが複数ルートから解決された場合は、アプリケーション側で重複を除外する。

`target_role` は認可用の `m_users.role` ではなく、通知専用の `m_notification_roles.role_code` を参照する。
