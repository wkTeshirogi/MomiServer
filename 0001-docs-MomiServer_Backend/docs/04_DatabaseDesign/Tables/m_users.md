# m_users

## 概要

ユーザーの認証情報、権限、作成日時、更新日時、最終ログイン日時を管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | ユーザーID |
| 2 | email | VARCHAR(255) | 不可 | なし | UNIQUE | メールアドレス |
| 3 | password | VARCHAR(255) | 不可 | なし | なし | ハッシュ化パスワード |
| 4 | role | VARCHAR(50) | 不可 | なし | なし | 権限 |
| 5 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 6 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |
| 7 | lastLoginAt | timestamp(3) | 可 | なし | なし | 最終ログイン日時 |
| 8 | username | VARCHAR(100) | 可 | なし | NULLまたは非空白 | 本人が設定する表示名。メールとは分離、重複可 |
| 9 | theme | VARCHAR(10) | 不可 | dark | light、dark | 個人テーマ |
| 10 | login_failure_count | integer | 不可 | 0 | 0以上 | Webログインの連続失敗回数 |
| 11 | login_locked_until | timestamp(3) | 可 | なし | なし | 一時ロックの解除予定日時。日本時間 |

V38でusername・themeを追加する。既存ユーザーは表示名未設定・ダークとなり、既存のログインは維持する。
名前の未設定は移行互換のためNULLを許容するが、個人設定画面から空欄への保存は許可しない。
V39でログインの実行状態を追加する。再起動後も保持し、設定保存用のテーブルは新設しない。
ロックの判定・解除・通知は[セキュリティ共通設定設計](../../11_SecurityDesign/0007_security_common_settings.md)を参照する。

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

## マスタ削除制御

ユーザーを通常利用から除外する場合は、物理削除ではなくマスタステータスを削除扱いの状態へ更新する。
削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。
