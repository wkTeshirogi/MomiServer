# t_notifications

## 概要

ブラウザ通知の通知本体を管理する。

通知内容、重要度、遷移先、発生元情報を保持し、ユーザーごとの既読状態は `t_user_notifications` で管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | DB内部ID |
| 2 | notification_id | UUID | 不可 | gen_random_uuid() | UNIQUE | 外部参照用通知ID |
| 3 | notification_type | VARCHAR(50) | 不可 | なし | CHECK | 通知種別 |
| 4 | level | VARCHAR(20) | 不可 | なし | CHECK | 通知レベル |
| 5 | title | VARCHAR(255) | 不可 | なし | なし | 通知タイトル |
| 6 | message | TEXT | 不可 | なし | なし | 通知本文 |
| 7 | source_type | VARCHAR(50) | 不可 | なし | CHECK | 通知発生元種別 |
| 8 | source_id | VARCHAR(100) | 可 | なし | なし | 発生元データID |
| 9 | link_path | VARCHAR(500) | 可 | なし | なし | ブラウザ通知クリック時の遷移先 |
| 10 | metadata_json | JSONB | 不可 | `{}` | JSON object | 補足情報 |
| 11 | expires_at | timestamp(3) | 可 | なし | なし | 表示期限 |
| 12 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 13 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 通知種別

| 値 | 内容 |
| --- | --- |
| security_alert | 不正アクセス、認証失敗、認可拒否などのセキュリティ通知 |
| batch_completed | バッチ実行完了通知 |
| batch_failed | バッチ実行失敗通知 |
| inventory_low | 在庫不足通知 |
| vehicle_inspection_expiry | 車検期限接近・超過通知 |
| system_notice | システム運用通知 |

## 通知レベル

| 値 | 内容 |
| --- | --- |
| info | 監査記録または軽微な情報通知 |
| warning | 管理者の確認が望ましい警告通知 |
| critical | 早急な確認が必要な重要通知 |

## 発生元種別

| 値 | 内容 |
| --- | --- |
| security_event | セキュリティ監査イベント |
| batch_execution | バッチ実行要求または実行履歴 |
| inventory | 在庫管理 |
| vehicle | 車両管理。source_idは車両ID |
| system | システム共通 |

## インデックス

| No | インデックス名 | 対象カラム | 用途 |
| --- | --- | --- | --- |
| 1 | uk_t_notifications_notification_id | notification_id | 外部参照用通知IDの一意制約 |
| 2 | idx_t_notifications_type_created | notification_type, created_dt | 通知種別ごとの時系列検索 |
| 3 | idx_t_notifications_level_created | level, created_dt | 通知レベルごとの時系列検索 |
| 4 | idx_t_notifications_source | source_type, source_id | 発生元データからの通知検索 |
| 5 | idx_t_notifications_expires_at | expires_at | 表示期限切れ通知の検索 |
| 6 | idx_t_notifications_created_dt | created_dt | 作成日時順検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## 関連実装

| 種別 | クラス | 内容 |
| --- | --- | --- |
| Repository | `common.repository.NotificationRepository` | 通知本体の作成、外部参照用通知IDによる取得を行う |
| DTO | `common.dto.BrowserNotificationCreateDto` | 通知本体の作成内容を保持する |
| DTO | `common.dto.BrowserNotificationDetailDto` | 通知本体の詳細情報を保持する |
| Utility | `common.util.Notifications` | ブラウザ通知登録と、WARNING / CRITICAL時のDiscord通知を行う |

## 備考

通知本文にはAPIキー、JWT、パスワード、Webhook URLなどの機密値を含めない。

通知の削除APIは初期実装では提供せず、表示期限やユーザー別の `dismissed` 状態で画面非表示を制御する。
