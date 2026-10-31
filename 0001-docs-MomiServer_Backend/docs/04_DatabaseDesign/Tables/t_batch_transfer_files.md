# t_batch_transfer_files

## 概要

バッチ実行に関連するアップロードファイルおよびダウンロード配布ファイルを実行要求単位で管理するテーブル。
ファイル本体はストレージに保存し、本テーブルではファイルメタ情報、転送方向、カテゴリ、ダウンロード UUID、転送状態を管理する。

ダウンロード配布ファイルは、バッチ実行要求作成後に `m_batch_files` を親データとして本テーブルへ展開する。
Agent からアップロードされるログや成果物は、実行結果として本テーブルへ直接登録する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | ファイル転送 ID |
| 2 | execution_request_id | BIGINT | 不可 | なし | 外部キー | 紐づくバッチ実行要求 ID |
| 3 | source_batch_file_id | BIGINT | 可 | なし | 外部キー | 元になるバッチ定義配布ファイル ID |
| 4 | agent_id | BIGINT | 不可 | なし | なし | 対象 Agent ID |
| 5 | direction | VARCHAR(20) | 不可 | なし | なし | 転送方向。`upload` または `download` |
| 6 | category | VARCHAR(50) | 不可 | なし | なし | ファイルカテゴリ |
| 7 | file_name | VARCHAR(255) | 不可 | なし | なし | ファイル名 |
| 8 | file_extension | VARCHAR(50) | 可 | なし | なし | ファイル拡張子 |
| 9 | content_type | VARCHAR(255) | 可 | なし | なし | Content-Type |
| 10 | file_size | BIGINT | 可 | なし | なし | ファイルサイズ |
| 11 | checksum_sha256 | VARCHAR(64) | 可 | なし | なし | SHA-256 ハッシュ |
| 12 | storage_path | VARCHAR(1000) | 不可 | なし | なし | 案: 設定された保存ルートからの相対パス |
| 13 | download_uuid | UUID | 可 | なし | 一意 | ダウンロード用の使い切り UUID |
| 14 | transfer_status | VARCHAR(30) | 不可 | なし | なし | 転送状態 |
| 15 | download_count | INTEGER | 不可 | 0 | なし | ダウンロード完了回数 |
| 16 | last_downloaded_dt | timestamp(3) | 可 | なし | なし | 最終ダウンロード完了日時 |
| 17 | expires_dt | timestamp(3) | 可 | なし | なし | UUID 有効期限 |
| 18 | regenerated_dt | timestamp(3) | 可 | なし | なし | UUID 再生成日時 |
| 19 | failure_reason | VARCHAR(1000) | 可 | なし | なし | 転送失敗理由 |
| 20 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 21 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## 値定義

### direction

| 値 | 説明 |
| --- | --- |
| upload | Agent から Server へ送信するファイル |
| download | Server から Agent へ配布するファイル |

### category

| 値 | 説明 |
| --- | --- |
| log | 実行ログ |
| artifact | 成果物 |
| input | 入力ファイル |
| script | 実行スクリプト |
| agent_update | Agent 更新ファイル |

### transfer_status

| 値 | 説明 |
| --- | --- |
| ready | ダウンロード可能 |
| uploading | アップロード受付中 |
| downloading | ダウンロード中 |
| completed | アップロード完了 |
| consumed | ダウンロード完了により UUID 消費済み |
| failed | 転送失敗 |
| expired | 有効期限切れ |
| deleted | 削除扱い |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_batch_transfer_files_execution_request_id | execution_request_id | 実行要求単位のファイル一覧取得 |
| 2 | idx_t_batch_transfer_files_source_batch_file_id | source_batch_file_id | 親ファイル単位の追跡 |
| 3 | idx_t_batch_transfer_files_agent_id | agent_id | Agent 単位のファイル取得 |
| 4 | idx_t_batch_transfer_files_category | category | カテゴリ別検索 |
| 5 | idx_t_batch_transfer_files_download_uuid | download_uuid | UUID ダウンロード |
| 6 | idx_t_batch_transfer_files_transfer_status | transfer_status | 状態別検索 |

## 外部キー

| 制約 | 参照元 | 参照先 | 削除時 |
| --- | --- | --- | --- |
| fk_t_batch_transfer_files_request | execution_request_id | t_batch_execution_requests.id | CASCADE |
| fk_t_batch_transfer_files_source | source_batch_file_id | m_batch_files.id | RESTRICT |

## 登録仕様

1. アップロードファイルは `direction = upload` として登録する。
2. Agent からアップロードされるログや成果物は `source_batch_file_id = null` として登録する。
3. ダウンロード配布ファイルは、バッチ実行要求作成後に `m_batch_files` から `direction = download` として登録する。
4. ダウンロード配布ファイルは `source_batch_file_id` に元の `m_batch_files.id` を保持する。
5. ダウンロード対象は `download_uuid` を発行し、Agent へ事前連携する。
6. ダウンロード完了通知を受けた場合、現在の UUID を消費済みにし、次回用 UUID を再生成する。
7. ファイル本体は DB に保存せず、`storage_path` で参照する。

保存名・保存先の設定は [ファイル転送詳細設計](../../02_SystemDesign/バッチ管理/Details/0005_batch_file_transfer_detail_design.md) に従う。
親ファイルから展開する場合は同じ相対パスを保持し、保存用UUIDを再発行しない。
