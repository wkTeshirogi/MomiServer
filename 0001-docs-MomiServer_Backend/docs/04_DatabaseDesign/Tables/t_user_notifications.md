# t_user_notifications

## 概要

ブラウザ通知のユーザー別状態を管理する。

通知本体は `t_notifications` に保持し、本テーブルでは対象ユーザー、配信状態、既読状態、非表示状態を保持する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | DB内部ID |
| 2 | notification_id | UUID | 不可 | なし | 外部キー | 外部参照用通知ID |
| 3 | user_id | BIGINT | 不可 | なし | 外部キー | 通知対象ユーザーID |
| 4 | delivery_status | VARCHAR(30) | 不可 | pending | CHECK | 配信状態 |
| 5 | read_status | VARCHAR(30) | 不可 | unread | CHECK | 既読状態 |
| 6 | delivered_at | timestamp(3) | 可 | なし | CHECK | 配信日時 |
| 7 | read_at | timestamp(3) | 可 | なし | CHECK | 既読日時 |
| 8 | dismissed_at | timestamp(3) | 可 | なし | CHECK | 非表示日時 |
| 9 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 10 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 配信状態

| 値 | 内容 |
| --- | --- |
| pending | ブラウザ側で未配信または未表示 |
| delivered | ブラウザ側へ配信または表示済み |
| failed | 配信または表示処理に失敗 |

## 既読状態

| 値 | 内容 |
| --- | --- |
| unread | 未読 |
| read | 既読 |
| dismissed | 非表示 |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | uk_t_user_notifications_user_notification | user_id, notification_id | ユーザーごとの同一通知重複防止 |
| 2 | idx_t_user_notifications_user_read | user_id, read_status, created_dt | ユーザー別未読通知検索 |
| 3 | idx_t_user_notifications_user_delivery | user_id, delivery_status, created_dt | ユーザー別配信状態検索 |
| 4 | idx_t_user_notifications_notification_id | notification_id | 通知本体からの対象ユーザー検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## 関連実装

| 種別 | クラス | 内容 |
| --- | --- | --- |
| Repository | `common.repository.NotificationRepository` | ユーザー別通知状態の作成、未読取得、件数取得、配信済み化、既読化、非表示化を行う |
| DTO | `common.dto.UserBrowserNotificationDetailDto` | ユーザー別通知状態を含む通知詳細を保持する |

## 備考

未読バッジは `read_status = 'unread'` の件数を利用する。

通知をユーザーが閉じた場合は物理削除せず、`read_status = 'dismissed'` と `dismissed_at` を更新する。
