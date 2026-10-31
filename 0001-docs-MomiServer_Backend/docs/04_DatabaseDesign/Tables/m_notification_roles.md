# m_notification_roles

## 概要

認可用の `m_users.role` とは独立した通知配信用ロールを管理する。

通知ロールは「在庫管理担当」「セキュリティ監査担当」「運用担当」など、通知を受け取る業務上の役割を表す。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | DB内部ID |
| 2 | role_code | VARCHAR(50) | 不可 | なし | UNIQUE | 通知ロールコード |
| 3 | role_name | VARCHAR(100) | 不可 | なし | なし | 通知ロール表示名 |
| 4 | description | TEXT | 可 | なし | なし | 通知ロール説明 |
| 5 | enabled | BOOLEAN | 不可 | true | なし | 有効フラグ |
| 6 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 7 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | uk_m_notification_roles_role_code | role_code | 通知ロールコードの一意制約 |
| 2 | idx_m_notification_roles_code_enabled | role_code, enabled | 通知ルート解決時の有効ロール検索 |

## 関連実装

| 種別 | クラス | 内容 |
| --- | --- | --- |
| Repository | `common.repository.NotificationRepository` | 通知ルート設定の `target_role` から通知ロールを検索する |

## 備考

`role_code` は `m_users.role` と混同しない。
ユーザー権限を変更せずに通知先だけを変更できるようにするため、通知ロールは独立して管理する。
