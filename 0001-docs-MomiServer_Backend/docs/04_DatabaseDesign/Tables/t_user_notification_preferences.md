# t_user_notification_preferences

## 概要

通知ルート設定で通知対象に解決されたユーザーが、通知種別ごとに実際に受け取るかを管理する。

このテーブルは通知先そのものを定義するのではなく、ユーザーごとの受信可否、最低通知レベル、通知チャネルを上書きするために使用する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | DB内部ID |
| 2 | user_id | BIGINT | 不可 | なし | 外部キー | 設定対象ユーザーID |
| 3 | notification_type | VARCHAR(50) | 不可 | なし | CHECK | 通知種別 |
| 4 | minimum_level | VARCHAR(20) | 不可 | info | CHECK | 受信対象とする最低通知レベル |
| 5 | browser_enabled | BOOLEAN | 不可 | true | なし | ブラウザ通知受信フラグ |
| 6 | discord_enabled | BOOLEAN | 不可 | true | なし | Discord通知受信フラグ |
| 7 | enabled | BOOLEAN | 不可 | true | なし | ユーザー別受信設定の有効フラグ |
| 8 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 9 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 制約

| No | 制約 | 内容 |
| --- | --- | --- |
| 1 | fk_t_user_notification_preferences_user | 設定対象ユーザーを `m_users.id` に紐づける |
| 2 | uk_t_user_notification_preferences_user_type | 1ユーザーにつき通知種別ごとに1設定のみ保持する |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_user_notification_preferences_user | user_id, notification_type, enabled | 通知対象ユーザーの受信設定検索 |

## 関連実装

| 種別 | クラス | 内容 |
| --- | --- | --- |
| Repository | `common.repository.NotificationRepository` | 通知ルートで解決したユーザーに対して、通知種別ごとの受信可否を判定する |

## 備考

設定レコードが存在しない場合は、通知ルート設定の内容をそのまま採用する。

`enabled = false` の場合は、その通知種別をユーザーが受け取らない設定として扱う。
