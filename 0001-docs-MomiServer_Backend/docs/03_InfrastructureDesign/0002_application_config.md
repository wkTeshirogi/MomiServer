# アプリケーション設定設計

## サーバ設定

| 項目 | 内容 |
| --- | --- |
| ポート | 環境変数で変更可能。未指定時は8080 |
| HTTP圧縮 | 有効 |
| タイムゾーン | Asia/Tokyo |
| JSON null項目 | 出力しない |

## プロファイル

| 項目 | 内容 |
| --- | --- |
| 既定プロファイル | dev |
| 想定プロファイル | dev、prod、test |

## データベース接続

| 項目 | 内容 |
| --- | --- |
| DB | PostgreSQL |
| 接続先 | ホスト、ポート、DB名を環境変数で変更可能 |
| ユーザー | 環境変数で変更可能 |
| パスワード | 環境変数で変更可能 |
| DDL自動更新 | validate |
| Open Session in View | 無効 |

## Flyway

| 項目 | 内容 |
| --- | --- |
| 有効化 | 有効 |
| baseline-on-migrate | 有効 |
| validate-on-migrate | 無効 |

## Spring Batch

| 項目 | 内容 |
| --- | --- |
| 起動時ジョブ自動実行 | 無効 |
| Batch用テーブル初期化 | always |
