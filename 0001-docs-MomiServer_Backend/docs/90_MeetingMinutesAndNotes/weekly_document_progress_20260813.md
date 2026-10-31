# 週次ドキュメント作業メモ 2026/08/13

## サマリ

SmartHomeWebServer のフロントエンドおよびバックエンド実装を確認し、既存ドキュメントとの差分を整理した。
今回の主な差分は、バッチ実行要求、Agent 連携、バッチ実行ファイル配布、バッチ定義・バッチスケジュール画面である。

既存ドキュメントリポジトリには未コミット変更が多数存在するため、既存変更の上書きを避け、追加済みドキュメントの確認と週次メモ作成を中心に実施した。

## 確認したソースコード範囲

| 区分 | パス | 確認範囲 |
| --- | --- | --- |
| フロントエンド | `D:\6000.PROJECTS\MomiServer\0001-src-smarthomewebserverfrontend` | React Router、認証ガード、マスタ画面、バッチ定義画面、バッチスケジュール画面、API 生成コード連携 |
| バックエンド | `D:\6000.PROJECTS\MomiServer\smarthomewebserverbackend` | Controller、Service、Repository、DTO、SecurityConfig、Flyway migration、MQTT publish、スケジューラ |
| ドキュメント | `D:\6000.PROJECTS\MomiServer\0001-docs-smarthomewebserverbackend` | DB 設計、API 設計、UI 設計、システム設計、テンプレート、索引 |

## 差分一覧

| 区分 | 実装側の確認内容 | 既存ドキュメントとの差分 | 反映方針 |
| --- | --- | --- | --- |
| バッチ実行要求 | `/api/batch/executions/definitions/{batchDefinitionId}` と `/api/batch/executions/{executionId}` が実装済み | API 設計書が不足 | API 単位で新規追加 |
| Agent 開始・完了通知 | `/api/agent/batch/executions/{executionId}/start`、`/complete` が実装済み | Agent API 設計書が不足 | API 単位で新規追加 |
| バッチ実行ファイル | 登録、一覧、download API と `t_batch_execution_files` が実装済み | DB/API 設計書が不足 | テーブル単位、API 単位で新規追加 |
| バッチ実行要求 DB | `t_batch_execution_requests` に UUID、dispatch、timeout、schedule fire time が実装済み | テーブル定義書が不足 | テーブル単位で新規追加 |
| バッチ定義画面 | 一覧・詳細・登録更新画面が実装済み | UI 設計書が不足 | 画面単位で新規追加 |
| バッチスケジュール画面 | 一覧・詳細・登録更新画面が実装済み | UI 設計書が不足 | 画面単位で新規追加 |
| ナビゲーション | 追加ドキュメントへの導線が不足 | `000_index.md` に未掲載 | 競合を避け、索引へ最小限の追記 |
| セキュリティ | Agent API が permitAll | 認証・署名・ネットワーク制限の運用仕様が未確定 | 即時確定せず確認事項に分離 |
| フロントエンド URL | `difinition` / `Difinition` 表記が実装に存在 | 表記ゆれの扱いが未確定 | 既存 URL を正として記録し、修正要否は確認事項に分離 |

## 作成したドキュメント

| 種別 | ファイル |
| --- | --- |
| DB テーブル定義 | `docs/04_DatabaseDesign/Tables/t_batch_execution_requests.md` |
| DB テーブル定義 | `docs/04_DatabaseDesign/Tables/t_batch_execution_files.md` |
| API 設計 | `docs/05_API_Design/API/0021_batch_execution_create_manual.md` |
| API 設計 | `docs/05_API_Design/API/0022_batch_execution_detail.md` |
| API 設計 | `docs/05_API_Design/API/0023_agent_batch_execution_start.md` |
| API 設計 | `docs/05_API_Design/API/0024_agent_batch_execution_complete.md` |
| API 設計 | `docs/05_API_Design/API/0025_batch_execution_file_register.md` |
| API 設計 | `docs/05_API_Design/API/0026_agent_batch_execution_file_list.md` |
| API 設計 | `docs/05_API_Design/API/0027_agent_batch_execution_file_download.md` |
| UI 設計 | `docs/06_UserInterfaceDesign/3001_batch_definition.md` |
| UI 設計 | `docs/06_UserInterfaceDesign/3002_batch_schedule.md` |
| 作業メモ | `docs/90_MeetingMinutesAndNotes/weekly_document_progress_20260813.md` |

## 更新したドキュメント

| ファイル | 更新内容 | 理由 |
| --- | --- | --- |
| `docs/04_DatabaseDesign/000_index.md` | バッチ実行要求・実行ファイルテーブルへのリンクを追加 | 新規テーブル定義書への導線確保 |
| `docs/05_API_Design/000_index.md` | バッチ実行要求、Agent API、実行ファイル API へのリンクを追加 | 新規 API 設計書への導線確保 |
| `docs/06_UserInterfaceDesign/0000_index.md` | バッチ定義・バッチスケジュール画面へのリンクを追加 | 新規 UI 設計書への導線確保 |

## 実装と既存ドキュメントの主な差分

| 区分 | 差分 |
| --- | --- |
| バッチ実行 | 実装では手動実行とスケジュール実行を `t_batch_execution_requests` で統合管理している。既存ドキュメントでは実行要求単位の API/DB 定義が不足していた。 |
| Agent 連携 | 実装では MQTT publish と HTTP start/complete 通知を組み合わせている。既存ドキュメントでは Agent 連携 API の個別仕様が不足していた。 |
| ファイル配布 | 実装ではファイルパス登録、SHA-256 算出、download を扱う。既存ドキュメントでは `t_batch_execution_files` と API 定義が不足していた。 |
| UI | 実装ではバッチ定義・バッチスケジュール画面が React コンポーネントとして存在する。既存 UI 設計には該当画面の記載が不足していた。 |
| 索引 | 追加ドキュメントが nav や索引に未掲載だったため、最小限のリンク追記が必要だった。 |

## 即時反映せず確認事項とした内容

| 区分 | 内容 | 理由 |
| --- | --- | --- |
| Agent API セキュリティ | `/api/agent/batch/executions/**` が permitAll である点の運用妥当性 | 認証方式、ネットワーク制限、署名検証の意図が実装だけでは確定できないため |
| URL 表記 | `difinition` / `Difinition` の typo と見える表記 | 既存 URL 互換性に影響するため、ドキュメントでは現行実装を記録し、修正判断は保留 |
| `mkdocs.yml` nav | 追加ドキュメントの nav 直接追加 | 既存の未コミット変更があり、ナビゲーション競合の可能性があるため |
| バックエンド未追跡 `docs/` | バックエンドリポジトリ配下の生成ドキュメント | 正式な格納先へ移管するか、生成物として除外するか判断が必要なため |

## `000_index.md` または `mkdocs.yml` 更新理由

| ファイル | 更新有無 | 理由 |
| --- | --- | --- |
| `docs/04_DatabaseDesign/000_index.md` | 更新 | 新規テーブル定義書への参照追加が必要なため |
| `docs/05_API_Design/000_index.md` | 更新 | 新規 API 設計書への参照追加が必要なため |
| `docs/06_UserInterfaceDesign/0000_index.md` | 更新 | 新規 UI 設計書への参照追加が必要なため |
| `mkdocs.yml` | 未更新 | 既存変更があり、ナビゲーション競合を避けるため |

## 確認結果

| 確認項目 | 結果 | 補足 |
| --- | --- | --- |
| Git 状態確認 | 完了 | ドキュメント側は既存の未コミット変更が多数あり |
| `git fetch` | 完了 | 3 リポジトリで実行 |
| MkDocs build | 成功 | nav 未登録ページ警告は継続 |
| フロントエンド差分 | 差分なし | `main...origin/main` |
| バックエンド差分 | 未追跡あり | `AGENTS.md` と `docs/` が未追跡 |

## 残っている課題、次回確認すべき事項

| 優先度 | 内容 | 目的 |
| --- | --- | --- |
| 高 | ドキュメントリポジトリの staged / unstaged 変更を整理する | 既存変更と今回追加分の混在を解消する |
| 高 | Agent API の認証・認可・通信経路制限を決める | permitAll の運用リスクを設計として明確化する |
| 中 | `mkdocs.yml` に新規ドキュメントを正式追加する | サイトナビゲーションから参照可能にする |
| 中 | バックエンド未追跡 `docs/` の扱いを決める | 生成物か正式ドキュメントかを切り分ける |
| 中 | フロントエンドの `difinition` 表記を修正するか判断する | URL 互換性と表記統一を両立する |

## 備考

- 今回はソースコードそのものの変更は行っていない。
- 既存の未コミット変更を削除、上書き、stash、reset はしていない。
- `git commit` と `git push` は実行していない。

