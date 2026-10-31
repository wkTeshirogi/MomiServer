# t_user_notification_roles

## 概要

ユーザーと通知専用ロールの割当を管理する。

1ユーザーに複数の通知ロールを割り当てられるため、在庫担当と運用担当のような複数の通知責務を表現できる。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | DB内部ID |
| 2 | user_id | BIGINT | 不可 | なし | 外部キー | 割当対象ユーザーID |
| 3 | notification_role_id | BIGINT | 不可 | なし | 外部キー | 通知ロールID |
| 4 | enabled | BOOLEAN | 不可 | true | なし | 割当有効フラグ |
| 5 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 6 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 制約

| No | 制約 | 内容 |
| --- | --- | --- |
| 1 | fk_t_user_notification_roles_user | 割当対象ユーザーを `m_users.id` に紐づける |
| 2 | fk_t_user_notification_roles_role | 通知ロールを `m_notification_roles.id` に紐づける |
| 3 | uk_t_user_notification_roles_user_role | 同一ユーザーへの同一通知ロール重複割当を防止する |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_user_notification_roles_role | notification_role_id, enabled | 通知ロールから対象ユーザーを検索 |
| 2 | idx_t_user_notification_roles_user | user_id, enabled | ユーザーに割り当てられた通知ロールを検索 |

## 関連実装

| 種別 | クラス | 内容 |
| --- | --- | --- |
| Repository | `common.repository.NotificationRepository` | 通知ロールに割り当てられた有効ユーザーを通知対象に展開する |

## 備考

通知対象から一時的に外す場合は物理削除ではなく `enabled = false` を利用する。
