# t_batch_execution_files

## 概要

バッチ実行要求に紐づく成果物ファイルを管理するテーブル。
Agent が取得する実行ファイルや、管理者が登録した実行結果ファイルの参照情報を保持する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | ファイル ID |
| 2 | execution_request_id | BIGINT | 不可 | なし | 外部キー | 紐づくバッチ実行要求 ID |
| 3 | file_name | VARCHAR(255) | 不可 | なし | なし | download 時に返すファイル名 |
| 4 | file_path | VARCHAR(1000) | 不可 | なし | なし | サーバー上の絶対ファイルパス |
| 5 | content_type | VARCHAR(255) | 可 | なし | なし | HTTP response の Content-Type |
| 6 | file_size | BIGINT | 不可 | なし | なし | ファイルサイズ |
| 7 | sha256 | VARCHAR(64) | 不可 | なし | なし | ファイル内容の SHA-256 ハッシュ |
| 8 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 9 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_batch_execution_files_execution_request_id | execution_request_id | 実行要求単位のファイル一覧取得 |

## 外部キー

| 制約 | 参照元 | 参照先 | 削除時 |
| --- | --- | --- | --- |
| fk_t_batch_execution_files_request | execution_request_id | t_batch_execution_requests.id | CASCADE |

## 登録仕様

1. `file_path` で指定されたパスを正規化する。
2. 通常ファイルとして存在しない場合はリソース未検出エラーとする。
3. `file_name` が未指定の場合は、ファイルパスの末尾名を使用する。
4. 登録時に `file_size` と `sha256` を算出する。
5. download 時は `content_type` が未設定の場合、`application/octet-stream` として返却する。

