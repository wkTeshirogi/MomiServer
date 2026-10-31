# t_batch_execution_histories

## 概要

バッチ実行結果とログ参照情報を履歴として管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | 実行履歴ID |
| 2 | batch_definition_id | BIGINT | 不可 | なし | 外部キー | バッチ定義ID |
| 3 | test_execution | BOOLEAN | 不可 | false | なし | テスト実行有無 |
| 4 | execution_started_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 実行開始日時 |
| 5 | execution_finished_dt | timestamp(3) | 可 | なし | なし | 実行終了日時 |
| 6 | execution_result | VARCHAR(50) | 不可 | なし | なし | 実行結果 |
| 7 | exit_code | INTEGER | 可 | なし | なし | 終了コード |
| 8 | log_path | VARCHAR(500) | 可 | なし | なし | ログパス |
| 9 | message | TEXT | 可 | なし | なし | メッセージ |
| 10 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 11 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## インデックス

| No | インデックス | カラム | 用途 |
| --- | --- | --- | --- |
| 1 | idx_t_batch_execution_histories_definition_id | batch_definition_id | バッチ定義単位の検索 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。

