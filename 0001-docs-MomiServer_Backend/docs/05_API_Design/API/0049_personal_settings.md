# 個人設定API

## 基本情報

`070_api_design.md`テンプレートを使用した。

| 項目 | 内容 |
| --- | --- |
| API ID | API-0049 |
| 分類 | 本人向け個人設定 |
| パス | `/api/users/me/settings`配下 |
| 認証 | Web JWT必須 |
| 権限 | ADMIN・OPERATOR・USER。本人だけ |
| 対象外 | メール・通知設定・ロール・他ユーザーの変更 |

## 概要

管理者用`/api/auth/settings`と分離し、対象ユーザーは認証情報から決定する。
リクエスト内の対象ID・メールアドレス・ロールは変更対象や更新条件に使用しない。

## 処理概要

| メソッド | 相対パス | 処理 | 正常応答 |
| --- | --- | --- | --- |
| GET | なし | 本人の設定取得 | 200・個人設定 |
| PUT | `/profile` | 表示名だけを更新 | 200・個人設定 |
| PUT | `/theme` | テーマだけを更新 | 200・個人設定 |
| POST | `/password` | パスワード変更・本人の全セッション失効 | 204・本文なし |

## リクエストボディ

| API | 項目 | 型 | 必須 | 制約 |
| --- | --- | --- | --- | --- |
| profile | username | string | 必須 | 非空白、100文字以内。前後の空白を除去して保存。表示名の重複は許可 |
| theme | theme | string | 必須 | `light`または`dark`。大文字・OS連動値は受け付けない |
| password | current_password | string | 必須 | 非空白、72文字以内。DBのハッシュと照合 |
| password | new_password | string | 必須 | 非空白、8文字以上、UTF-8で72バイト以内。現在と異なる値 |
| password | confirm_password | string | 必須 | 新しいパスワードと一致。72文字以内 |

パスワードは前後空白を除去せず照合・保存する。BCryptの入力上限をUTF-8バイト数でも検証する。
パスワード更新・セッション失効は同一トランザクションとし、他ユーザーのセッションへ影響させない。
現在のパスワード不一致は400とし、JWT失効を表す401とは区別する。

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| username | stringまたはnull | 本人の表示名。未設定時も項目を省略せずnullを返す |
| theme | string | 保存済みテーマ。既定値`dark` |

ハッシュ・パスワード原文・メール・ロールは返却しない。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 必須項目、文字数の不正 |
| 400 | MALFORMED_REQUEST | JSON・テーマコードの不正 |
| 400 | INVALID_REQUEST | 現パスワード不一致、確認不一致、同一パスワード、UTF-8上限超過 |
| 401 | UNAUTHORIZED | 未認証、または無効なWeb JWT |
| 403 | ACCESS_DENIED | BAN_USER・Agent・外部キー等、通常Webロール以外 |
| 404 | RESOURCE_NOT_FOUND | 認証情報に対応するユーザーが存在しない |

## 備考

- V38で追加するカラムは[m_users定義](../../04_DatabaseDesign/Tables/m_users.md)を参照する。
- 既存データへの適用はFlyway実行後にjOOQを再生成する。既存の登録・管理者設定APIの契約は変更しない。
- [個人設定テスト](../../08_TestDesign/0013_personal_settings_test.md)で実JWTの失効と本人境界を確認する。
