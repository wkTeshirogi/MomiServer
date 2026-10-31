# m_batch_files

## 概要

V25で実装。バッチ定義に添付したファイルの不変な実体参照を管理する。差し替えは新規行と新しい保存用UUIDを作り、旧行をdeletedにする。作成済み要求はJSONの添付スナップショットから旧行を参照できる。

## カラム定義

| カラム | 型 | NULL | 制約・既定値 | 用途 |
| --- | --- | --- | --- | --- |
| id | BIGSERIAL | 不可 | 主キー | 添付管理ID |
| batch_definition_id | BIGINT | 不可 | m_batch_definitions参照 | 対象定義 |
| upload_request_id | UUID | 不可 | 一意 | 同じアップロードの再送識別 |
| file_name | VARCHAR(255) | 不可 | なし | 元のファイル名（表示用） |
| destination_path | VARCHAR(500) | 不可 | なし | 実行領域からの相対配置先 |
| file_name_key | VARCHAR(500) | 不可 | active行で定義内一意 | 小文字化した配置先 |
| file_category | VARCHAR(20) | 不可 | script/input/config | 種別 |
| content_type | VARCHAR(255) | 可 | NULL | 予約メタ情報。配布はapplication/octet-stream |
| file_size | BIGINT | 不可 | 0以上 | 実測バイト数 |
| checksum_sha256 | VARCHAR(64) | 不可 | なし | 本体ハッシュ |
| storage_path | VARCHAR(1000) | 不可 | 一意 | 保存ルートからの相対パス。現実装はUUIDのみ |
| status | VARCHAR(20) | 不可 | active、active/deletedのみ | 定義への添付状態 |
| replaces_id | BIGINT | 可 | m_batch_files参照 | 差し替え前の行 |
| created_dt / updated_dt | TIMESTAMPTZ | 不可 | CURRENT_TIMESTAMP | 登録・更新日時 |

## 更新制御

定義行ロックで添付・差し替え・解除・実行スナップショット作成を直列化する。ファイルの保存・検証後にメタ情報を確定する。同じupload_request_idは対象定義・相対パス・種別・差し替え元・本体ハッシュを照合し、一致すれば同じ行を返す。
解除はstatus更新で行う。旧版や未参照実体の自動物理削除は行わず、作成済み要求の参照を保護する。

## インデックス・外部キー

主キー、upload_request_idとstorage_pathの一意制約、active行の(batch_definition_id,file_name_key)部分一意索引を持つ。定義と差し替え元への外部キーは物理削除を制限する。

## 既存案との相違

t_batch_file_upload_requests・t_batch_transfer_filesを追加する案は今回使用せず、添付行の要求IDと実行要求のattachments JSONで管理する。配布はAgent専用認証と実行・端末世代の認可を必須とする。
