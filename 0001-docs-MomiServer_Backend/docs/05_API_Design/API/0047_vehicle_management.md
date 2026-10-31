# 車両管理

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0047 |
| 分類 | 車両管理 |
| パス | `/api/vehicles`および`/api/vehicle-imports`配下 |
| 認証 | JWT認証必須 |
| 正常ステータス | HTTP 200 |

## エンドポイント

| メソッド | パス | 権限 | 用途 |
| --- | --- | --- | --- |
| GET | `/api/vehicles` | ADMIN・OPERATOR・USER | 管理中の車両一覧取得 |
| GET | `/api/vehicles/{id}` | ADMIN・OPERATOR・USER | 車両詳細取得 |
| GET | `/api/vehicles/{id}/history` | ADMIN・OPERATOR・USER | 車検期限変更履歴取得 |
| POST | `/api/vehicles` | ADMIN・OPERATOR | 車両新規登録 |
| PUT | `/api/vehicles/{id}` | ADMIN・OPERATOR | 車両更新 |
| PUT | `/api/vehicles/{id}/inspection` | ADMIN・OPERATOR | 車検情報だけの更新 |
| POST | `/api/vehicles/{id}/archive` | ADMIN | 車両の管理終了 |
| POST | `/api/vehicle-imports` | ADMIN・OPERATOR | PDF・画像をアップロードして候補を作成 |
| GET | `/api/vehicle-imports/{importId}` | ADMIN・OPERATOR | 取込候補を取得 |
| GET | `/api/vehicle-imports/{importId}/source` | ADMIN・OPERATOR | 確認中だけ原本を取得。`Cache-Control: no-store` |
| POST | `/api/vehicle-imports/{importId}/confirm` | ADMIN・OPERATOR | VehicleSaveRequestで確認登録 |
| POST | `/api/vehicle-imports/{importId}/cancel` | ADMIN・OPERATOR | 取込を取消し、HTTP 204を返す |

## 一覧・詳細レスポンス

| 項目 | 型 | 内容 |
| --- | --- | --- |
| id | long | 車両ID |
| display_name | string | 表示名 |
| registration_number | string | 登録番号 |
| chassis_number | string | 車台番号 |
| vehicle_identification_code | string | 車両識別符号 |
| vehicle_name | string | 車名 |
| model_code | string | 型式 |
| first_registration_month | string | 初度登録年月。`YYYY-MM`形式 |
| inspection_expiry_date | date | 車検有効期間満了日 |
| issued_date | date | 交付年月日 |
| usage_category | string | 用途区分の英語値 |
| purpose | string | 用途の英語値 |
| status | string | VALID、DUE_SOON、URGENT、EXPIRED、UNKNOWN、ARCHIVED |
| days_remaining | long | 現在日から満了日までの日数。超過時は負数 |
| notes | string | 備考 |
| created_by | string | 登録者 |
| updated_by | string | 最終更新者 |
| createdAt | datetime | 登録日時 |
| updatedAt | datetime | 更新日時 |

一覧はEXPIRED、URGENT、DUE_SOON、UNKNOWN、VALIDの順とし、同じ状態では満了日、車両IDの順とする。
ARCHIVEDは通常一覧に含めず、IDを指定した詳細では参照できる。

## 登録・更新リクエスト

表示名、登録番号、車台番号、車名、車検有効期間満了日は必須とする。
車台番号、車両識別符号、登録番号と初度登録年月を使用して管理中の車両との重複候補を判定する。
期限を変更した場合は変更前後の満了日と操作者を履歴へ保存する。

## 車検更新リクエスト

`PUT /api/vehicles/{id}/inspection`は、年数でなく確定した日付を受け付ける。
正常時は状態・残日数を再計算した車両詳細を返す。

| 項目 | 型 | 必須 | 契約 |
| --- | --- | --- | --- |
| previous_expiry_date | date | 必須 | 画面で取得した登録済み満了日。DBと異なる場合は409 |
| inspection_expiry_date | date | 必須 | 新しい満了日。現在の満了日より後の日付 |
| issued_date | date | 任意 | 交付年月日。省略・nullは登録済みの値を維持 |

日付形式は`YYYY-MM-DD`とする。交付日の削除は通常の車両編集で行う。
管理終了車両は更新不可。現在日より未来であることは必須とせず、期限切れ車両の過去の更新記録も登録できる。
車両名・識別情報・備考などは更新しない。満了日更新と履歴保存は同一トランザクションで行う。
行ロック後に更新前満了日を照合するため、同じ要求の再送は期限をさらに延長せず409となる。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 必須項目、文字数、年月形式が不正 |
| 400 | INVALID_REQUEST | 重複候補、管理終了済み車両の更新、車検更新で満了日が同日・過去 |
| 401 | UNAUTHORIZED | 未認証 |
| 403 | ACCESS_DENIED | 操作に必要なロールがない |
| 404 | RESOURCE_NOT_FOUND | 対象車両が存在しない |
| 409 | OPTIMISTIC_LOCK_CONFLICT | 車検更新時に登録済み満了日が変更されている |

## ファイル取込契約

アップロードはmultipartの`file`フィールドで受け付け、PDF、PNG、JPEGを署名検証したうえで10 MiB以下、
PDF 10ページ以下に限定する。multipart解析前に`Content-Length`を検証し、未指定はHTTP 411、
上限超過はHTTP 413とする。暗号化・破損・形式不一致、画像またはPDFページの展開画素数超過はHTTP 400とする。
成功時は`id`、`status`、MIMEタイプから生成した非識別`display_filename`、`detected_media_type`、
`extraction_method`、`candidate`、`warnings`、`expires_at`を返す。利用者指定ファイル名は保存・返却しない。
`candidate`は表示名、登録番号、車台番号、車名、型式、初度登録年月、満了日、交付日だけを含む。

OCRのTesseract実行ファイルまたは言語データが利用不能な場合、取込はHTTP 400で失敗し、原本を削除する。利用者は既存の手動登録を継続できる。全取込APIはUSERへHTTP 403、未認証へHTTP 401を返す。

## 対象外

期限通知作成APIは後続実装とする。
