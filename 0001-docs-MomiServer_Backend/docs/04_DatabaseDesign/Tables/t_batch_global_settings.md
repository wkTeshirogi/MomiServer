# t_batch_global_settings

## 概要

バッチ共通設定の単一行。V20で作成し、V25で添付制限と作業領域保持を追加する。

## カラム定義

| カラム | 型 | NULL | 初期値・制約 | 意味 |
| --- | --- | --- | --- | --- |
| id | SMALLINT | 不可 | 主キー、1のみ | 単一設定行 |
| setting_version | BIGINT | 不可 | 1、正数 | 保存ごとに増加 |
| max_concurrent_executions | INTEGER | 不可 | 3、1以上 | 端末ごとの同時実行数 |
| default_timeout_seconds | INTEGER | 不可 | 1800、1以上 | 標準タイムアウト |
| result_retry_interval_seconds | INTEGER | 不可 | 300、1以上 | 再送間隔 |
| result_retry_count | INTEGER | 不可 | 5、0以上 | 再送回数 |
| unsent_result_retention_days | INTEGER | 不可 | 7、1以上 | 未送信結果保持 |
| result_wait_timeout_seconds | INTEGER | 不可 | 3600、標準タイムアウトより長い | 結果待ち期限 |
| start_wait_timeout_seconds | INTEGER | 不可 | 3600、1以上 | 開始待ち期限 |
| cancel_grace_seconds | INTEGER | 不可 | 30、1以上 | 停止猶予 |
| attachment_max_file_mib | INTEGER | 可 | NULL、1～10240 | 1ファイル上限MiB |
| attachment_max_total_mib | INTEGER | 可 | NULL、1～102400 | 定義ごとの合計MiB |
| attachment_max_count | INTEGER | 可 | NULL、1～1000 | 添付数上限 |
| work_directory_retention_days | INTEGER | 可 | NULL、1～3650 | 作業領域保持日数 |
| created_dt / updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | 作成・更新日時 |

## 更新制御

V25の4項目がNULLの場合はバックエンド設定ファイルの初期値を使用する。取得APIは解決済み値を返し、画面で保存した値を優先する。旧クライアントが4項目を省略して保存した場合は既存値（未設定なら初期値）を維持する。
上限・保持の適用方針は[添付詳細設計](../../02_SystemDesign/バッチ管理/Details/0005_batch_file_transfer_detail_design.md)を参照。

## インデックス・外部キー

idの主キーのみ。外部キーなし。
