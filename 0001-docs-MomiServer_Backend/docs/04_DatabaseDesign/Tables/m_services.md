# m_services

## 概要

監視対象サービスの基本情報、対象マシン、状態、ログ参照方法、操作可否、通知設定を管理する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | サービスID |
| 2 | service_name | VARCHAR(255) | 不可 | なし | なし | Windowsサービス名またはsystemd unit名 |
| 3 | view_name | VARCHAR(255) | 不可 | なし | なし | 表示名 |
| 4 | target_machine | BIGINT | 不可 | なし | なし | 対象マシンID |
| 5 | status | VARCHAR(50) | 不可 | なし | なし | 状態 |
| 6 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 作成日時 |
| 7 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |
| 8 | log_path | VARCHAR(255) | 可 | なし | なし | ログパス |
| 9 | use_journalctl | BOOLEAN | 不可 | false | なし | journalctl利用有無 |
| 10 | allow_start | BOOLEAN | 不可 | false | なし | 起動許可 |
| 11 | allow_stop | BOOLEAN | 不可 | false | なし | 停止許可 |
| 12 | allow_restart | BOOLEAN | 不可 | false | なし | 再起動許可 |
| 13 | auto_restart | BOOLEAN | 不可 | false | なし | 自動再起動有無 |
| 14 | notice_level | VARCHAR(50) | 不可 | none | なし | 通知レベル |
| 15 | description | TEXT | 可 | なし | なし | 説明 |

## 更新制御

レコード更新時に更新日時を現在日時へ更新する。
Agentは`service_name`をOSサービス管理機構へそのまま渡すため、表示名ではなく実在する管理名を登録する。

## マスタ削除制御

サービスを通常利用から除外する場合は、物理削除ではなく `status` を削除扱いの状態へ更新する。
削除状態はステータスで管理し、`delete_flag`、`deleted`、`is_deleted` などの削除専用フラグは保持しない。
