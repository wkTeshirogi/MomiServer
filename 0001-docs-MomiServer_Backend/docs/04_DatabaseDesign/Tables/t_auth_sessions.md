# t_auth_sessions

## 概要

認証済み利用者のリフレッシュトークンセッションを管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | session_id | UUID | 不可 | なし | 主キー | セッションID |
| 2 | user_email | VARCHAR(255) | 不可 | なし | 外部キー | 利用者メールアドレス |
| 3 | refresh_token_hash | VARCHAR(64) | 不可 | なし | なし | リフレッシュトークンハッシュ |
| 4 | expires_at | timestamp(3) | 不可 | なし | なし | 有効期限 |
| 5 | last_used_at | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 最終利用日時 |
| 6 | revoked | BOOLEAN | 不可 | false | なし | 失効状態 |
| 7 | revoked_at | timestamp(3) | 可 | なし | なし | 失効日時 |
| 8 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 9 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_auth_sessions_user_email | user_email | 利用者単位の検索 |
| 2 | idx_t_auth_sessions_expires_at | expires_at | 期限切れ判定 |
| 3 | idx_t_auth_sessions_revoked | revoked | 失効状態判定 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

