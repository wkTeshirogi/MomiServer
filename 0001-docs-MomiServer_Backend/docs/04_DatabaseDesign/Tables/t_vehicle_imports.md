# t_vehicle_imports

## 概要

車検証PDF・画像取込の確認待ち候補と原本の一時保存参照を管理する。原本バイト列、OCR全文、所有者・使用者の氏名および住所は保存しない。

## カラム

| カラム | 型 | 必須 | 内容 |
| --- | --- | --- | --- |
| id | UUID | Yes | 取込ID |
| display_filename | varchar(255) | Yes | MIMEタイプから生成した非識別表示名。利用者指定名は保存しない |
| detected_media_type | varchar(64) | Yes | 署名から判定したMIMEタイプ |
| storage_reference | varchar(64) | Yes | UUID形式の一時保存参照。利用者ファイル名は含めない |
| status | varchar(32) | Yes | PENDING_REVIEW、REGISTERED、CANCELLED、PARSE_FAILED、EXPIRED |
| display_name | varchar(100) | No | 車両表示名候補 |
| registration_number | varchar(50) | No | 登録番号候補 |
| chassis_number | varchar(100) | No | 車台番号候補 |
| vehicle_name | varchar(100) | No | 車名候補 |
| model_code | varchar(100) | No | 型式候補 |
| first_registration_month | varchar(7) | No | 初度登録年月候補 |
| inspection_expiry_date | date | No | 車検満了日候補 |
| issued_date | date | No | 交付年月日候補 |
| extraction_method | varchar(32) | No | PDF_TEXTまたはOCR |
| warnings | text | No | 確認画面用の項目不足・OCR警告。全文は含めない |
| expires_at | timestamp(3) | Yes | 確認可能期限 |
| created_by | varchar(255) | Yes | 取込実行者 |
| created_dt / updated_dt | timestamp(3) | Yes | 作成・更新日時 |
| confirmed_vehicle_id | bigint | No | 登録完了時のt_vehicles参照 |

`status, expires_at` にインデックスを持つ。終端状態では物理原本を削除し、残存した場合も定期回収の対象とする。
